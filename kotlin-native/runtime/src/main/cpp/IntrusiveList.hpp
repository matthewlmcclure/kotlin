/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <cstddef>
#include <iterator>
#include <limits>

#if __has_include(<optional>)
#include <optional>
#elif __has_include(<experimental/optional>)
// TODO: Remove when wasm32 is gone.
#include <experimental/optional>
namespace std {
template <typename T>
using optional = std::experimental::optional<T>;
inline constexpr auto nullopt = std::experimental::nullopt;
} // namespace std
#else
#error "No <optional>"
#endif

#include "KAssert.h"
#include "Utils.hpp"

namespace kotlin {

template <typename T>
struct DefaultIntrusiveForwardListTraits {
    static T* next(const T& value) noexcept { return value.next(); }

    static void setNext(T& value, T* next) noexcept { value.setNext(next); }

    static bool trySetNext(T& value, T* next) noexcept { return value.trySetNext(next); }
};

// Intrusive variant of `std::forward_list`. Notable differences:
// * The container does not own nodes. Care must be taken not to allow a node
//   to be in two containers at once, or twice into the same container.
// * The container is move-only, and moving invalidates `before_begin` iterator.
// * insert_after, erase_after take `iterator` instead of `const_iterator`, because
//   they do in fact require mutability via `Traits::setNext`.
// * When the node leaves the container, nothing clears `next` pointer inside it.
// * Has fallible `try_push_front` which uses `Traits::trySetNext` on a new candidate item.
// * Has fallible `try_pop_front`.
//
// `Traits` must have 2 methods:
// static T* next(const T& value);
// static void setNext(T& value, T* next);
// static bool trySetNext(T& value, T* next);
// NOTE: `trySetNext`, `setNext` and `next` must be callable even on uninitialized `T` (i.e. they
//       should only access storage inside `T`).
template <typename T, typename Traits = DefaultIntrusiveForwardListTraits<T>>
class intrusive_forward_list : private MoveOnly {
public:
    using value_type = T;
    using size_type = size_t;
    using difference_type = ptrdiff_t;
    using reference = value_type&;
    using const_reference = const value_type&;
    using pointer = value_type*;
    using const_pointer = const value_type*;

    class iterator {
    public:
        using difference_type = intrusive_forward_list::difference_type;
        using value_type = intrusive_forward_list::value_type;
        using pointer = intrusive_forward_list::pointer;
        using reference = intrusive_forward_list::reference;
        using iterator_category = std::forward_iterator_tag;

        iterator() noexcept = default;
        iterator(const iterator&) noexcept = default;
        iterator& operator=(const iterator&) noexcept = default;

        reference operator*() noexcept { return *node_; }

        pointer operator->() noexcept { return node_; }

        iterator& operator++() noexcept {
            node_ = next(node_);
            return *this;
        }

        iterator operator++(int) noexcept {
            auto result = *this;
            ++(*this);
            return result;
        }

        bool operator==(const iterator& rhs) const noexcept { return node_ == rhs.node_; }
        bool operator!=(const iterator& rhs) const noexcept { return !(*this == rhs); }

    private:
        friend class intrusive_forward_list;

        explicit iterator(pointer node) noexcept : node_(node) {}

        intrusive_forward_list::pointer node_ = nullptr;
    };

    class const_iterator {
    public:
        using difference_type = intrusive_forward_list::difference_type;
        using value_type = const intrusive_forward_list::value_type;
        using pointer = intrusive_forward_list::const_pointer;
        using reference = intrusive_forward_list::const_reference;
        using iterator_category = std::forward_iterator_tag;

        const_iterator() noexcept = default;
        const_iterator(const const_iterator&) noexcept = default;
        const_iterator& operator=(const const_iterator&) noexcept = default;

        const_iterator(iterator it) noexcept : node_(it.node_) {}

        reference operator*() noexcept { return *node_; }

        pointer operator->() noexcept { return node_; }

        const_iterator& operator++() noexcept {
            node_ = next(node_);
            return *this;
        }

        const_iterator operator++(int) noexcept {
            auto result = *this;
            ++(*this);
            return result;
        }

        bool operator==(const const_iterator& rhs) const noexcept { return node_ == rhs.node_; }
        bool operator!=(const const_iterator& rhs) const noexcept { return !(*this == rhs); }

    private:
        friend class intrusive_forward_list;

        explicit const_iterator(pointer node) noexcept : node_(node) {}

        pointer node_ = nullptr;
    };

    intrusive_forward_list() noexcept { clear(); }

    intrusive_forward_list(intrusive_forward_list&& rhs) noexcept {
        // Since tail() is shared, there's no need to update the last node's next_.
        setNext(head(), next(rhs.head()));
        rhs.clear();
    }

    template <typename InputIt>
    intrusive_forward_list(InputIt first, InputIt last) noexcept : intrusive_forward_list() {
        assign(std::move(first), std::move(last));
    }

    ~intrusive_forward_list() = default;

    intrusive_forward_list& operator=(intrusive_forward_list&& rhs) noexcept {
        intrusive_forward_list tmp(std::move(rhs));
        swap(tmp);
        return *this;
    }

    void swap(intrusive_forward_list& rhs) noexcept {
        // Since tail() is shared, there's no need to swap the last nodes' next_.
        using std::swap;
        auto thisNext = next(head());
        auto rhsNext = next(rhs.head());
        swap(thisNext, rhsNext);
        setNext(head(), thisNext);
        setNext(rhs.head(), rhsNext);
    }

    template <typename InputIt>
    void assign(InputIt first, InputIt last) noexcept {
        clear();
        insert_after(before_begin(), std::move(first), std::move(last));
    }

    reference front() noexcept { return *next(head()); }
    const_reference front() const noexcept { return *next(head()); }

    iterator before_begin() noexcept { return iterator(head()); }
    const_iterator before_begin() const noexcept { return const_iterator(head()); }
    const_iterator cbefore_begin() const noexcept { return const_iterator(head()); }

    iterator begin() noexcept { return iterator(next(head())); }
    const_iterator begin() const noexcept { return const_iterator(next(head())); }
    const_iterator cbegin() const noexcept { return const_iterator(next(head())); }

    iterator end() noexcept { return iterator(tail()); }
    const_iterator end() const noexcept { return const_iterator(tail()); }
    const_iterator cend() const noexcept { return const_iterator(tail()); }

    bool empty() const noexcept { return next(head()) == tail(); }

    size_type max_size() const noexcept { return std::numeric_limits<size_type>::max(); }

    void clear() noexcept { setNext(head(), tail()); }

    iterator insert_after(iterator pos, reference value) noexcept {
        RuntimeAssert(pos != end(), "Attempted to insert_after end()");
        RuntimeAssert(pos != iterator(), "Attempted to insert_after empty iterator");
        setNext(&value, next(pos.node_));
        setNext(pos.node_, &value);
        return iterator(&value);
    }

    template <typename InputIt>
    iterator insert_after(iterator pos, InputIt first, InputIt last) noexcept {
        RuntimeAssert(pos != end(), "Attempted to insert_after end()");
        RuntimeAssert(pos != iterator(), "Attempted to insert_after empty iterator");
        pointer nextNode = next(pos.node_);
        pointer prevNode = pos.node_;
        for (auto it = first; it != last; ++it) {
            pointer newNode = &*it;
            setNext(prevNode, newNode);
            prevNode = newNode;
        }
        setNext(prevNode, nextNode);
        return iterator(prevNode);
    }

    iterator erase_after(iterator pos) noexcept {
        RuntimeAssert(pos != end(), "Attempted to erase_after end()");
        RuntimeAssert(pos != iterator(), "Attempted to erase_after empty iterator");
        pointer nextNode = next(next(pos.node_));
        setNext(pos.node_, nextNode);
        return iterator(nextNode);
    }

    iterator erase_after(iterator first, iterator last) noexcept {
        RuntimeAssert(first != end(), "Attempted to erase_after starting at end()");
        RuntimeAssert(first != iterator(), "Attempted to erase_after starting at empty iterator");
        RuntimeAssert(last != iterator(), "Attempted to erase_after ending at empty iterator");
        setNext(first.node_, last.node_);
        return last;
    }

    void push_front(reference value) noexcept { insert_after(before_begin(), value); }

    bool try_push_front(reference value) noexcept { return try_insert_after(before_begin(), value) != std::nullopt; }

    void pop_front() noexcept { erase_after(before_begin()); }

    pointer try_pop_front() noexcept {
        pointer top = next(head());
        if (top == tail()) {
            return nullptr;
        }
        setNext(head(), next(top));
        return top;
    }

    void remove(reference value) noexcept {
        // TODO: no need to move on after finding the first match.
        return remove_if([&value](const_reference x) noexcept { return &x == &value; });
    }

    template <typename P>
    void remove_if(P p) noexcept(noexcept(p(std::declval<const_reference>()))) {
        pointer prev = head();
        pointer node = next(prev);
        while (node != tail()) {
            if (p(*node)) {
                // The node is being removed.
                node = next(node);
                setNext(prev, node);
            } else {
                // The node is staying.
                prev = node;
                node = next(node);
            }
        }
    }

    // TODO: Implement splice_after.

private:
    static pointer next(const_pointer node) noexcept { return Traits::next(*node); }

    static void setNext(pointer node, pointer next) noexcept { return Traits::setNext(*node, next); }

    static bool trySetNext(pointer node, pointer next) noexcept { return Traits::trySetNext(*node, next); }

    pointer head() noexcept { return reinterpret_cast<pointer>(headStorage_); }
    const_pointer head() const noexcept { return reinterpret_cast<const_pointer>(headStorage_); }

    static pointer tail() noexcept { return reinterpret_cast<pointer>(tailStorage_); }

    // TODO: Consider making public.
    std::optional<iterator> try_insert_after(iterator pos, reference value) noexcept {
        RuntimeAssert(pos != end(), "Attempted to try_insert_after end()");
        RuntimeAssert(pos != iterator(), "Attempted to try_insert_after empty iterator");
        if (!trySetNext(&value, next(pos.node_))) {
            return std::nullopt;
        }
        setNext(pos.node_, &value);
        return iterator(&value);
    }

    alignas(value_type) char headStorage_[sizeof(value_type)] = {0};
    alignas(value_type) static inline char tailStorage_[sizeof(value_type)] = {0};
};

template <typename InputIt>
intrusive_forward_list(InputIt, InputIt) -> intrusive_forward_list<typename std::iterator_traits<InputIt>::value_type>;

} // namespace kotlin
