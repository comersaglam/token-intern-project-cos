package com.example.app_pos.network.mapper

import com.example.app_pos.model.TransactionType
import com.example.app_pos.network.dto.ApprovalCreateDto

/**
 * Approval → wire. Request direction only, on purpose.
 *
 * app-pos can SEND a write for approval, but it has no approvals inbox yet (the Room
 * entity and DAO exist as a skeleton, and there is no domain type to map an ApprovalDto
 * onto). app-mobile is the side that renders approvals today; when app-pos grows its own
 * inbox, the response-direction mapper lands here alongside a domain model — and the
 * unknown-value rule for status/initiator_role/channel is to drop the row, since an
 * approval that cannot be rendered is worse than one that is not shown.
 */

/**
 * Builds the "send for approval" body.
 *
 * [initiatorRole] says which of the three lines this is, and [targetUserId] is the party
 * who must answer. The rule the server also enforces: whoever starts a request is never
 * the one who approves it.
 */
fun approvalCreateDto(
    sellerId: String,
    customerId: String,
    amountMinor: Long,
    type: TransactionType,
    description: String?,
    initiatorRole: String,
    targetUserId: String
): ApprovalCreateDto = ApprovalCreateDto(
    sellerId = sellerId,
    customerId = customerId,
    amountMinor = amountMinor,
    type = type.name,
    description = description,
    initiatorRole = initiatorRole,
    targetUserId = targetUserId
)
