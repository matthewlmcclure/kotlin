/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <atomic>
#include <cstddef>

#include "Allocator.hpp"
#include "GCScheduler.hpp"
#include "IntrusiveList.hpp"
#include "MarkAndSweepUtils.hpp"
#include "ObjectFactory.hpp"
#include "ScopedThread.hpp"
#include "Types.h"
#include "Utils.hpp"
#include "GCState.hpp"
#include "std_support/Memory.hpp"

namespace kotlin {

namespace mm {
class ThreadData;
}

namespace gc {

class FinalizerProcessor;

// Stop-the-world parallel mark + concurrent sweep. The GC runs in a separate thread, finalizers run in another thread of their own.
// TODO: Also make marking run concurrently with Kotlin threads.
class ConcurrentMarkAndSweep : private Pinned {
public:
    class ObjectData {
    public:
        ObjectData* next() const noexcept { return next_.load(std::memory_order_relaxed); }
        void setNext(ObjectData* next) noexcept { next_.store(next, std::memory_order_relaxed); }
        bool trySetNext(ObjectData* next) noexcept {
            ObjectData* expected = nullptr;
            return next_.compare_exchange_strong(expected, next, std::memory_order_relaxed);
        }

        bool marked() const noexcept { return next() != nullptr; }

        bool tryResetMark() noexcept {
            if (next() == nullptr) return false;
            setNext(nullptr);
            return true;
        }

    private:
        std::atomic<ObjectData*> next_ = nullptr;
    };

    enum MarkingBehavior { kMarkOwnStack, kDoNotMark };

    using MarkQueue = intrusive_forward_list<ObjectData>;

    class ThreadData : private Pinned {
    public:
        using ObjectData = ConcurrentMarkAndSweep::ObjectData;
        using Allocator = AllocatorWithGC<Allocator, ThreadData>;

        explicit ThreadData(ConcurrentMarkAndSweep& gc, mm::ThreadData& threadData, GCSchedulerThreadData& gcScheduler) noexcept :
            gc_(gc), threadData_(threadData), gcScheduler_(gcScheduler) {}
        ~ThreadData() = default;

        void SafePointAllocation(size_t size) noexcept;

        void ScheduleAndWaitFullGC() noexcept;
        void ScheduleAndWaitFullGCWithFinalizers() noexcept;

        void OnOOM(size_t size) noexcept;

        void OnSuspendForGC() noexcept;

        Allocator CreateAllocator() noexcept { return Allocator(gc::Allocator(), *this); }

    private:
        friend ConcurrentMarkAndSweep;
        ConcurrentMarkAndSweep& gc_;
        mm::ThreadData& threadData_;
        GCSchedulerThreadData& gcScheduler_;
        std::atomic<bool> marking_;
    };

    using Allocator = ThreadData::Allocator;

    ConcurrentMarkAndSweep(mm::ObjectFactory<ConcurrentMarkAndSweep>& objectFactory, GCScheduler& scheduler) noexcept;
    ~ConcurrentMarkAndSweep();

    void StartFinalizerThreadIfNeeded() noexcept;
    void StopFinalizerThreadIfRunning() noexcept;
    bool FinalizersThreadIsRunning() noexcept;
    void SetMarkingBehaviorForTests(MarkingBehavior markingBehavior) noexcept;
    void SetMarkingRequested() noexcept;
    void WaitForThreadsReadyToMark() noexcept;
    size_t CollectRootSetAndStartMarking() noexcept;

private:
    // Returns `true` if GC has happened, and `false` if not (because someone else has suspended the threads).
    bool PerformFullGC(int64_t epoch) noexcept;
    void MergeMarkStats(MarkStats stats) noexcept;

    mm::ObjectFactory<ConcurrentMarkAndSweep>& objectFactory_;
    GCScheduler& gcScheduler_;

    uint64_t lastGCTimestampUs_ = 0;
    GCStateHolder state_;
    ScopedThread gcThread_;
    std_support::unique_ptr<FinalizerProcessor> finalizerProcessor_;

    MarkQueue markQueue_;
    MarkStats lastGCMarkStats_;
    MarkingBehavior markingBehavior_;
};

namespace internal {
struct MarkTraits {
    using MarkQueue = gc::ConcurrentMarkAndSweep::MarkQueue;

    static void clear(MarkQueue& queue) noexcept {
        queue.clear();
    }

    static ObjHeader* tryDequeue(MarkQueue& queue) noexcept {
        if (auto* top = queue.try_pop_front()) {
            auto node = mm::ObjectFactory<gc::ConcurrentMarkAndSweep>::NodeRef::From(*top);
            return node->GetObjHeader();
        }
        return nullptr;
    }

    static void enqueue(MarkQueue& queue, ObjHeader* object) noexcept {
        auto& objectData = mm::ObjectFactory<gc::ConcurrentMarkAndSweep>::NodeRef::From(object).ObjectData();
        queue.try_push_front(objectData);
    }

    static void mark(ObjHeader* object) noexcept {
        auto& objectData = mm::ObjectFactory<gc::ConcurrentMarkAndSweep>::NodeRef::From(object).ObjectData();
        objectData.setNext(reinterpret_cast<ConcurrentMarkAndSweep::ObjectData*>(1));
    }

    static void processInMark(MarkQueue& markQueue, ObjHeader* object) noexcept {
        auto process = object->type_info()->processObjectInMark;
        RuntimeAssert(process != nullptr, "Got null processObjectInMark for object %p", object);
        process(static_cast<void*>(&markQueue), object);
    }
};
} // namespace internal

} // namespace gc
} // namespace kotlin
