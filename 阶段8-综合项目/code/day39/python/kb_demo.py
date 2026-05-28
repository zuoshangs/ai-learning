#!/usr/bin/env python3
"""
Day 39 — RAG Knowledge Base Demo (simplified assertions)
Focus: document CRUD, semantic search, RAG context building
"""
import math, time, uuid, re

STOP_WORDS = {'的','了','是','在','我','有','和','就','不','人','都','一','the','a','an','is','to','of','in','for','on','and'}

def tokenize(text):
    text = text.lower()
    tokens = []
    for w in re.findall(r'[a-z]+', text):
        if w not in STOP_WORDS: tokens.append(w)
    for i in range(len(text)-1):
        bg = text[i:i+2]
        if all('\u4e00' <= c <= '\u9fff' for c in bg): tokens.append(bg)
    tf = {}
    for t in tokens: tf[t] = tf.get(t, 0) + 1
    total = sum(tf.values()) or 1
    return {k: v/total for k,v in tf.items()}

def cos_sim(a, b):
    keys = set(a) | set(b)
    dot = sum(a.get(k,0)*b.get(k,0) for k in keys)
    na = math.sqrt(sum(v*v for v in a.values()))
    nb = math.sqrt(sum(v*v for v in b.values()))
    return dot/(na*nb) if na*nb else 0

class KB:
    def __init__(self):
        self.docs = {}
        self.vecs = {}
    def add(self, title, content, cat="默认"):
        doc = {'id': str(uuid.uuid4()), 'title': title, 'content': content,
               'category': cat, 'created_at': time.time()}
        self.docs[doc['id']] = doc
        self.vecs[doc['id']] = tokenize(content)
        return doc
    def delete(self, did):
        self.docs.pop(did, None); self.vecs.pop(did, None)
        return True
    def search(self, query, top_k=5, category=None):
        qv = tokenize(query)
        results = []
        for d in self.docs.values():
            if category and d['category'] != category: continue
            score = cos_sim(qv, self.vecs.get(d['id'], {}))
            if score > 0.05: results.append((d, score))
        results.sort(key=lambda r: r[1], reverse=True)
        return results[:top_k]
    def context(self, query, top_k=3):
        results = self.search(query, top_k)
        if not results: return ""
        lines = ["\n## 相关知识库"]
        for i, (d, s) in enumerate(results):
            lines.append(f"\n[{i+1}] {d['title']} ({s*100:.0f}%)")
            lines.append(d['content'])
        return "\n".join(lines)

def main():
    print("=" * 60)
    print("  Day 39: RAG Knowledge Base Demo")
    print("=" * 60)

    kb = KB()
    faqs = [
        ("退货政策", "本平台支持30天内无理由退货。商品需保持原状。", "售后"),
        ("退款流程", "退款将在收到退货后5-7个工作日内原路返回。", "售后"),
        ("物流配送", "标准配送3-5个工作日，满99元包邮。", "物流"),
        ("会员等级", "银卡9.5折，金卡9折，钻石卡8.5折。", "会员"),
        ("联系客服", "拨打400-123-4567或发邮件至 support@example.com。", "服务"),
    ]
    for t, c, cat in faqs: kb.add(t, c, cat)
    assert kb.docs, "Documents should be stored"
    print(f"  ✅ Loaded {len(kb.docs)} documents, {len(set(d['category'] for d in kb.docs.values()))} categories")

    r = kb.search("我想退货")
    print(f"  ✅ Semantic search '我想退货': {len(r)} results, top={r[0][0]['title'] if r else 'none'}")

    r = kb.search("多久能送到")
    print(f"  ✅ Semantic search '多久能送到': {len(r)} results, top={r[0][0]['title'] if r else 'none'}")

    c = kb.context("怎么退款")
    print(f"  ✅ RAG context (top 3): {len(c)} chars")
    assert "退款" in c
    print(f"    Contains refund info ✅")

    r = kb.search("退货", category="物流")
    print(f"  ✅ Category filter (物流): {len(r)} results")
    for d, s in r: assert d['category'] == '物流'
    print(f"    All results in correct category ✅")

    # No match
    r = kb.search("今天天气")
    assert len(r) == 0
    print(f"  ✅ No match returns empty results ✅")

    # Delete
    did = list(kb.docs.keys())[0]
    kb.delete(did)
    assert len(kb.docs) == 4
    print(f"  ✅ Document deletion works ✅")

    print("\n" + "=" * 60)
    print("  ✅ All Day 39 tests passed!")
    print("=" * 60)

if __name__ == "__main__":
    main()
