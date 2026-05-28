#!/usr/bin/env python3
"""
Day 40 — Ticket System Demo
Smart Customer Service Platform: work order CRUD + status workflow + stats
"""
import json
import time
import uuid
from dataclasses import dataclass, field
from typing import Optional


# ============================================================
# Ticket Model
# ============================================================
@dataclass
class Ticket:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    title: str = ""
    description: str = ""
    category: str = "其他"
    priority: str = "MEDIUM"  # LOW, MEDIUM, HIGH, URGENT
    status: str = "PENDING"   # PENDING, IN_PROGRESS, RESOLVED, CLOSED
    creator_name: str = "匿名用户"
    assignee: Optional[str] = None
    resolution: Optional[str] = None
    created_at: float = field(default_factory=time.time)
    resolved_at: Optional[float] = None

    @property
    def preview(self) -> str:
        return self.description[:60] + "..." if len(self.description) > 60 else self.description

    @property
    def resolution_time(self) -> str:
        if not self.resolved_at:
            return "-"
        hours = (self.resolved_at - self.created_at) / 3600
        if hours < 1: return "< 1h"
        return f"{hours:.1f}h"


# ============================================================
# Ticket Service
# ============================================================
VALID_TRANSITIONS = {
    "PENDING": ["IN_PROGRESS", "CLOSED"],
    "IN_PROGRESS": ["RESOLVED", "PENDING"],
    "RESOLVED": ["CLOSED", "IN_PROGRESS"],
    "CLOSED": [],
}


class TicketService:
    def __init__(self):
        self.tickets: dict[str, Ticket] = {}
        self._seed()

    def create(self, title: str, description: str, category: str = "其他",
               priority: str = "MEDIUM", creator: str = "匿名用户") -> Ticket:
        t = Ticket(title=title, description=description, category=category,
                    priority=priority.upper(), creator_name=creator)
        self.tickets[t.id] = t
        return t

    def assign(self, ticket_id: str, assignee: str) -> Ticket:
        t = self._get(ticket_id)
        t.assignee = assignee
        if t.status == "PENDING":
            t.status = "IN_PROGRESS"
        return t

    def update_status(self, ticket_id: str, new_status: str,
                       resolution: Optional[str] = None) -> Ticket:
        t = self._get(ticket_id)
        if new_status not in VALID_TRANSITIONS.get(t.status, []):
            raise ValueError(f"Cannot transition from {t.status} to {new_status}")
        t.status = new_status
        if new_status == "RESOLVED":
            t.resolved_at = time.time()
            if resolution:
                t.resolution = resolution
        return t

    def get(self, ticket_id: str) -> Optional[Ticket]:
        return self.tickets.get(ticket_id)

    def list(self, status: Optional[str] = None, priority: Optional[str] = None,
             category: Optional[str] = None) -> list[Ticket]:
        results = list(self.tickets.values())
        if status: results = [t for t in results if t.status == status.upper()]
        if priority: results = [t for t in results if t.priority == priority.upper()]
        if category: results = [t for t in results if t.category == category]
        return sorted(results, key=lambda t: t.created_at, reverse=True)

    def delete(self, ticket_id: str) -> bool:
        return self.tickets.pop(ticket_id, None) is not None

    def stats(self) -> dict:
        s = {"total": len(self.tickets), "pending": 0, "inProgress": 0,
             "resolved": 0, "closed": 0, "byPriority": {}, "byCategory": {},
             "totalResolutionHours": 0, "resolvedCount": 0, "avgResolutionHours": 0}
        for t in self.tickets.values():
            s[t.status.lower() if t.status != "IN_PROGRESS" else "inProgress"] += 1
            s["byPriority"][t.priority] = s["byPriority"].get(t.priority, 0) + 1
            s["byCategory"][t.category] = s["byCategory"].get(t.category, 0) + 1
            if t.resolved_at:
                hours = (t.resolved_at - t.created_at) / 3600
                s["totalResolutionHours"] += hours
                s["resolvedCount"] += 1
        if s["resolvedCount"] > 0:
            s["avgResolutionHours"] = round(s["totalResolutionHours"] / s["resolvedCount"], 1)
        return s

    def _get(self, ticket_id: str) -> Ticket:
        t = self.tickets.get(ticket_id)
        if not t: raise KeyError(f"Ticket not found: {ticket_id}")
        return t

    def _seed(self):
        self.create("无法登录账号", "用户反馈无法登录账号，尝试重置密码仍无法登录。",
                     "账号", "HIGH", "张三")
        self.create("退货退款申请", "购买商品与描述不符，要求退货退款。订单号：ORD-2026-0520。",
                     "售后", "URGENT", "李四")
        self.create("物流延迟查询", "快递已3天未更新物流信息。订单号：ORD-2026-0518。",
                     "物流", "MEDIUM", "王五")

        # Simulate resolve the second ticket
        t2 = list(self.tickets.values())[1]
        t2.status = "RESOLVED"
        t2.assignee = "客服B"
        t2.resolved_at = t2.created_at + 10800  # 3h
        t2.resolution = "已同意退货申请"


# ============================================================
# Tests
# ============================================================
def test_create_ticket():
    print("  [Test 1] Create Ticket")
    svc = TicketService()
    # Work on a fresh service
    svc2 = TicketService()
    svc2.tickets = {}
    svc2.categories = set()

    t = svc2.create("测试工单", "这是一个测试", "售后", "HIGH", "测试用户")
    assert t.id is not None
    assert t.status == "PENDING"
    assert t.priority == "HIGH"
    print(f"    Created: {t.title} (id={t.id[:8]}...) ✅")

    t2 = svc2.get(t.id)
    assert t2 is not None
    print(f"    Get by ID: OK ✅")


def test_status_workflow():
    print("\n  [Test 2] Status Workflow")
    svc = TicketService()
    svc.tickets = {}
    t = svc.create("工单流程测试", "测试工单流转", "其他", "LOW")

    # PENDING → IN_PROGRESS
    svc.update_status(t.id, "IN_PROGRESS")
    assert t.status == "IN_PROGRESS"
    print(f"    PENDING → IN_PROGRESS ✅")

    # IN_PROGRESS → RESOLVED
    svc.update_status(t.id, "RESOLVED", "已处理完成")
    assert t.status == "RESOLVED"
    assert t.resolution == "已处理完成"
    assert t.resolved_at is not None
    print(f"    IN_PROGRESS → RESOLVED (resolution set) ✅")

    # RESOLVED → CLOSED
    svc.update_status(t.id, "CLOSED")
    assert t.status == "CLOSED"
    print(f"    RESOLVED → CLOSED ✅")

    # CLOSED cannot transition
    try:
        svc.update_status(t.id, "PENDING")
        assert False, "Should not allow CLOSED → PENDING"
    except ValueError:
        print(f"    CLOSED → PENDING rejected (correct) ✅")


def test_assign():
    print("\n  [Test 3] Assign Ticket")
    svc = TicketService()
    svc.tickets = {}
    t = svc.create("分配测试", "测试分配功能")

    svc.assign(t.id, "客服A")
    assert t.assignee == "客服A"
    assert t.status == "IN_PROGRESS"  # Auto-transition
    print(f"    Assigned to 客服A, status → IN_PROGRESS ✅")


def test_stats():
    print("\n  [Test 4] Statistics")
    svc = TicketService()  # Has 3 seeded tickets

    s = svc.stats()
    print(f"    Total: {s['total']}")
    print(f"    Pending: {s['pending']}, InProgress: {s['inProgress']}, Resolved: {s['resolved']}")
    print(f"    Avg resolution: {s['avgResolutionHours']}h")
    assert s['total'] == 3
    assert s['pending'] == 2  # 2 pending + 1 resolved (from seed)
    assert s['resolved'] == 1
    print(f"    Stats correct ✅")


def test_filters():
    print("\n  [Test 5] Filters")
    svc = TicketService()  # 3 tickets: 账号(HIGH), 售后(URGENT), 物流(MEDIUM)

    urgent = svc.list(priority="URGENT")
    assert len(urgent) == 1
    print(f"    Filter priority=URGENT: {len(urgent)} ✅")

    pending = svc.list(status="PENDING")
    assert len(pending) == 2
    print(f"    Filter status=PENDING: {len(pending)} ✅")

    # All
    all_t = svc.list()
    assert len(all_t) == 3
    print(f"    No filter: {len(all_t)} ✅")


def test_full_scenario():
    print("\n  [Test 6] Full Workflow")
    svc = TicketService()
    svc.tickets = {}
    svc.categories = set()

    # Customer reports issue
    t = svc.create("订单号ORD-2026-0601未收到货", "已下单5天仍未收到，快递单号SF123456。",
                    "物流", "HIGH", "小明")
    print(f"    1. Customer creates ticket (PENDING) ✅")

    # Auto-assign
    svc.assign(t.id, "客服C")
    print(f"    2. Auto-assigned to 客服C (IN_PROGRESS) ✅")

    # Investigate
    svc.update_status(t.id, "RESOLVED", "已联系快递公司核实，预计明日送达。赠送20元优惠券作为补偿。")
    print(f"    3. Resolved with compensation ✅")

    s = svc.stats()
    assert s['resolved'] == 1
    print(f"    4. Stats: {s['resolved']} resolved out of {s['total']} ✅")
    print(f"    5. Resolution time: {t.resolution_time} ✅")


def main():
    print("=" * 60)
    print("  Day 40: Ticket System Demo")
    print("  Smart Customer Service Platform")
    print("=" * 60)

    test_create_ticket()
    test_status_workflow()
    test_assign()
    test_stats()
    test_filters()
    test_full_scenario()

    print("\n" + "=" * 60)
    print("  ✅ All Day 40 tests passed!")
    print("  Features: CRUD + Workflow + Assign + Stats\n")
    print("  Next: Day 41 — Admin Dashboard + LLMOps Integration 📊")
    print("=" * 60)


if __name__ == "__main__":
    main()
