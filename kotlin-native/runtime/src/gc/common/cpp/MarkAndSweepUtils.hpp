/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef RUNTIME_GC_COMMON_MARK_AND_SWEEP_UTILS_H
#define RUNTIME_GC_COMMON_MARK_AND_SWEEP_UTILS_H

#include "ExtraObjectData.hpp"
#include "FinalizerHooks.hpp"
#include "GlobalData.hpp"
#include "Logging.hpp"
#include "Memory.h"
#include "ObjectOps.hpp"
#include "ObjectTraversal.hpp"
#include "RootSet.hpp"
#include "Runtime.h"
#include "StableRefRegistry.hpp"
#include "ThreadData.hpp"
#include "Types.h"

namespace kotlin {
namespace gc {

namespace internal {

template <typename Traits>
void processFieldInMark(void* state, ObjHeader* field) noexcept {
    auto& markQueue = *static_cast<typename Traits::MarkQueue*>(state);
    if (field->heap()) {
        Traits::enqueue(markQueue, field);
    }
}

template <typename Traits>
void processObjectInMark(void* state, ObjHeader* object) noexcept {
    auto* typeInfo = object->type_info();
    RuntimeAssert(typeInfo != theArrayTypeInfo, "Must not be an array of objects");
    for (int i = 0; i < typeInfo->objOffsetsCount_; ++i) {
        auto* field = *reinterpret_cast<ObjHeader**>(reinterpret_cast<uintptr_t>(object) + typeInfo->objOffsets_[i]);
        if (!field) continue;
        processFieldInMark<Traits>(state, field);
    }
}

template <typename Traits>
void processArrayInMark(void* state, ArrayHeader* array) noexcept {
    RuntimeAssert(array->type_info() == theArrayTypeInfo, "Must be an array of objects");
    auto* begin = ArrayAddressOfElementAt(array, 0);
    auto* end = ArrayAddressOfElementAt(array, array->count_);
    for (auto* it = begin; it != end; ++it) {
        auto* field = *it;
        if (!field) continue;
        processFieldInMark<Traits>(state, field);
    }
}

template <typename Traits>
bool collectRoot(typename Traits::MarkQueue& markQueue, ObjHeader* object) noexcept {
    if (isNullOrMarker(object))
        return false;

    if (object->heap()) {
        Traits::enqueue(markQueue, object);
    } else {
        // Each permanent and stack object has own entry in the root set, so it's okay to only process objects in heap.
        Traits::processInMark(markQueue, object);
        RuntimeAssert(!object->has_meta_object(), "Non-heap object %p may not have an extra object data", object);
    }
    return true;
}

} // namespace internal

struct MarkStats {
    // How many objects are alive.
    size_t aliveHeapSet = 0;
    // How many objects are alive in bytes. Note: this does not include overhead of malloc/mimalloc itself.
    size_t aliveHeapSetBytes = 0;
    // How many roots are were marked.
    size_t rootSetSize = 0;

    void merge(MarkStats other) {
        aliveHeapSet += other.aliveHeapSet;
        aliveHeapSetBytes += other.aliveHeapSetBytes;
        rootSetSize += other.rootSetSize;
    }
};

template <typename Traits>
MarkStats Mark(typename Traits::MarkQueue& markQueue) noexcept {
    MarkStats stats;
    auto timeStart = konan::getTimeMicros();
    while (ObjHeader* top = Traits::tryDequeue(markQueue)) {
        RuntimeAssert(top->heap(), "Got non-heap reference %p in mark queue, permanent=%d stack=%d", top, top->permanent(), top->local());

        stats.aliveHeapSet++;
        stats.aliveHeapSetBytes += mm::GetAllocatedHeapSize(top);

        Traits::processInMark(markQueue, top);

        if (auto* extraObjectData = mm::ExtraObjectData::Get(top)) {
            if (auto weakCounter = extraObjectData->GetWeakReferenceCounter()) {
                RuntimeAssert(
                        weakCounter->heap(), "Weak counter must be a heap object. object=%p counter=%p permanent=%d local=%d", top,
                        weakCounter, weakCounter->permanent(), weakCounter->local());
                Traits::enqueue(markQueue, weakCounter);
            }
        }
    }
    auto timeEnd = konan::getTimeMicros();
    RuntimeLogDebug({kTagGC}, "Marked %zu objects in %" PRIu64 " microseconds in thread %d", stats.aliveHeapSet, timeEnd - timeStart, konan::currentThreadId());
    return stats;
}

template <typename Traits>
void SweepExtraObjects(typename Traits::ExtraObjectsFactory& objectFactory) noexcept {
    objectFactory.ProcessDeletions();
    auto iter = objectFactory.LockForIter();
    for (auto it = iter.begin(); it != iter.end();) {
        auto &extraObject = *it;
        if (!extraObject.getFlag(mm::ExtraObjectData::FLAGS_IN_FINALIZER_QUEUE) && !Traits::IsMarkedByExtraObject(extraObject)) {
            extraObject.ClearWeakReferenceCounter();
            if (extraObject.HasAssociatedObject()) {
                extraObject.DetachAssociatedObject();
                extraObject.setFlag(mm::ExtraObjectData::FLAGS_IN_FINALIZER_QUEUE);
                ++it;
            } else {
                extraObject.Uninstall();
                objectFactory.EraseAndAdvance(it);
            }
        } else {
            ++it;
        }
    }
}

template <typename Traits>
typename Traits::ObjectFactory::FinalizerQueue Sweep(typename Traits::ObjectFactory::Iterable& objectFactoryIter) noexcept {
    typename Traits::ObjectFactory::FinalizerQueue finalizerQueue;

    for (auto it = objectFactoryIter.begin(); it != objectFactoryIter.end();) {
        if (Traits::TryResetMark(*it)) {
            ++it;
            continue;
        }
        auto* objHeader = it->GetObjHeader();
        if (HasFinalizers(objHeader)) {
            objectFactoryIter.MoveAndAdvance(finalizerQueue, it);
        } else {
            objectFactoryIter.EraseAndAdvance(it);
        }
    }

    return finalizerQueue;
}

template <typename Traits>
typename Traits::ObjectFactory::FinalizerQueue Sweep(typename Traits::ObjectFactory& objectFactory) noexcept {
    auto iter = objectFactory.LockForIter();
    return Sweep<Traits>(iter);
}

template <typename Traits>
size_t collectRootSetForThread(typename Traits::MarkQueue& markQueue, mm::ThreadData& thread) {
    thread.gc().OnStoppedForGC();
    size_t stack = 0;
    size_t tls = 0;
    // TODO: Remove useless mm::ThreadRootSet abstraction.
    for (auto value : mm::ThreadRootSet(thread)) {
        if (internal::collectRoot<Traits>(markQueue, value.object)) {
            switch (value.source) {
                case mm::ThreadRootSet::Source::kStack:
                    ++stack;
                    break;
                case mm::ThreadRootSet::Source::kTLS:
                    ++tls;
                    break;
            }
        }
    }
    RuntimeLogDebug({kTagGC}, "Collected root set for thread stack=%zu tls=%zu", stack, tls);
    return stack + tls;
}

template <typename Traits>
size_t collectRootSetGlobals(typename Traits::MarkQueue& markQueue) noexcept {
    mm::StableRefRegistry::Instance().ProcessDeletions();
    size_t global = 0;
    size_t stableRef = 0;
    // TODO: Remove useless mm::GlobalRootSet abstraction.
    for (auto value : mm::GlobalRootSet()) {
        if (internal::collectRoot<Traits>(markQueue, value.object)) {
            switch (value.source) {
                case mm::GlobalRootSet::Source::kGlobal:
                    ++global;
                    break;
                case mm::GlobalRootSet::Source::kStableRef:
                    ++stableRef;
                    break;
            }
        }
    }
    RuntimeLogDebug({kTagGC}, "Collected global root set global=%zu stableRef=%zu", global, stableRef);
    return global + stableRef;
}

// TODO: This needs some tests now.
template <typename Traits, typename F>
size_t collectRootSet(typename Traits::MarkQueue& markQueue, F&& filter) noexcept {
    Traits::clear(markQueue);
    size_t size = 0;
    for (auto& thread : mm::GlobalData::Instance().threadRegistry().LockForIter()) {
        if (!filter(thread))
            continue;
        thread.Publish();
        size += collectRootSetForThread<Traits>(markQueue, thread);
    }
    size += collectRootSetGlobals<Traits>(markQueue);
    return size;
}

} // namespace gc
} // namespace kotlin

#endif // RUNTIME_GC_COMMON_MARK_AND_SWEEP_UTILS_H
