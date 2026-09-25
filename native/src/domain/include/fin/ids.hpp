// fin/ids.hpp — Strong ID types (skill §1: strong types, no implicit conversion)
//
// Maps Java model identifiers:
//   BillId, ItemId, BuyerId, SupplierId, TemplateId, PurchaseBillId, TransactionId,
//   TransportId, VariableId, CategoryId, ExpenseId, ExpenseAccountId, LabelPrintHistoryId,
//   AccountId (ledger), UserId (auth).
#pragma once
#include <cstdint>
#include <functional>

namespace fin {

#define FIN_DEFINE_ID(Name) \
  struct Name { \
    std::int64_t value{0}; \
    constexpr Name() = default; \
    constexpr explicit Name(std::int64_t v) : value(v) {} \
    constexpr bool operator==(const Name& o) const noexcept { return value == o.value; } \
    constexpr bool operator!=(const Name& o) const noexcept { return value != o.value; } \
    constexpr bool operator<(const Name& o) const noexcept { return value < o.value; } \
    constexpr bool valid() const noexcept { return value > 0; } \
    constexpr bool null() const noexcept { return value == 0; } \
  }

FIN_DEFINE_ID(BillId);
FIN_DEFINE_ID(ItemId);
FIN_DEFINE_ID(BuyerId);
FIN_DEFINE_ID(SupplierId);
FIN_DEFINE_ID(TemplateId);
FIN_DEFINE_ID(PurchaseBillId);
FIN_DEFINE_ID(TransactionId);
FIN_DEFINE_ID(TransportId);
FIN_DEFINE_ID(VariableId);
FIN_DEFINE_ID(CategoryId);
FIN_DEFINE_ID(ExpenseId);
FIN_DEFINE_ID(ExpenseAccountId);
FIN_DEFINE_ID(LabelPrintHistoryId);
FIN_DEFINE_ID(AccountId);
FIN_DEFINE_ID(UserId);

#undef FIN_DEFINE_ID

} // namespace fin

namespace std {
#define FIN_HASH_ID(Name) \
  template<> struct hash<fin::Name> { \
    std::size_t operator()(const fin::Name& x) const noexcept { \
      return std::hash<std::int64_t>{}(x.value); \
    } \
  }
FIN_HASH_ID(BillId);
FIN_HASH_ID(ItemId);
FIN_HASH_ID(BuyerId);
FIN_HASH_ID(SupplierId);
FIN_HASH_ID(TemplateId);
FIN_HASH_ID(PurchaseBillId);
FIN_HASH_ID(TransactionId);
FIN_HASH_ID(TransportId);
FIN_HASH_ID(VariableId);
FIN_HASH_ID(CategoryId);
FIN_HASH_ID(ExpenseId);
FIN_HASH_ID(ExpenseAccountId);
FIN_HASH_ID(LabelPrintHistoryId);
FIN_HASH_ID(AccountId);
FIN_HASH_ID(UserId);
#undef FIN_HASH_ID
}
