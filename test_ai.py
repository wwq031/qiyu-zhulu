import requests, json, time, sys
API = "http://localhost:5000"

def wait():
    for i in range(30):
        try:
            if requests.get(f"{API}/api/factions", timeout=3).status_code == 200: return
        except: time.sleep(1)
    sys.exit("Server not ready")

def new_game():
    requests.post(f"{API}/api/new-game", json={"faction_id": "fengtian_border", "policies": []})

def set_ai(prov):
    requests.post(f"{API}/api/config", json={"provider": prov, "api_key": "sk-6a2a4e05f3c9467fa22807467f887106"})

def test(order, sandbox=False):
    r = requests.post(f"{API}/api/custom-order/auto", json={"order": order, "sandbox": sandbox}, timeout=60)
    return r.json()

wait()

tests = [
    # NORMAL + DeepSeek
    ("NORMAL", "派间谍渗透进北京，窃取北洋军的作战计划", False),
    ("NORMAL", "在奉天秘密建立一座兵工厂，仿制德国毛瑟步枪", False),
    ("NORMAL", "派遣使节团出访欧美列强，争取外交承认和贷款", False),
    ("NORMAL", "利用奉天铁路枢纽的优势，对过往商队征收特别通行税", False),
    ("NORMAL", "组建一支由猎户和土匪组成的山地突击队，专门骚扰敌军后勤线", False),
    ("NORMAL", "截获一批走私的日本军火，将其藏匿在长白山中备用", False),
    ("NORMAL", "派遣密使与满蒙恢复会秘密接触，尝试策反其内部将领", False),
    ("NORMAL", "请来德国退役军官担任军事顾问，全面改革训练体系", False),
    # SANDBOX + Local
    ("SANDBOX", "给我十万大军，踏平中原", True),
    ("SANDBOX", "吞并满蒙恢复会，把他们所有领土和部队都收编过来", True),
    ("SANDBOX", "召唤一支装备了德国克虏伯大炮的装甲师，部署在奉天城外", True),
    ("SANDBOX", "全属性拉满，让我的势力一夜之间成为超级大国", True),
    ("SANDBOX", "秒建一条从奉天到北京的铁路，同时在沿途每座城市部署守备队", True),
    ("SANDBOX", "撤销满蒙恢复会的存在，把他们从地图上彻底抹去", True),
    ("SANDBOX", "用魔法把国库填满黄金，然后给每个士兵发一年军饷", True),
]

print("=" * 60)
print(f"  AI Test Suite: {len(tests)} cases (Normal + Sandbox)")
print("=" * 60)

passed = 0
for i, (mode, order, sandbox) in enumerate(tests):
    new_game()
    set_ai("deepseek" if not sandbox else "local")

    s0 = requests.get(f"{API}/api/state").json()
    b_t, b_ap = s0['treasury'], s0['action_points']

    r = test(order, sandbox)

    s1 = requests.get(f"{API}/api/state").json()
    a_t, a_ap = s1['treasury'], s1['action_points']

    prov = r.get('provider', '?')
    feas = r.get('feasibility', '?')
    cost = r.get('cost', {})
    effects = r.get('effects', {})
    acts = r.get('action_results')
    changed = (b_t != a_t or b_ap != a_ap or acts)
    ok = "OK" if changed else "??"
    if changed: passed += 1

    print(f"\n[{i+1:>2}] {mode:7s} {prov:8s} {feas:8s} 💰{b_t}→{a_t} AP={b_ap}→{a_ap} [{ok}]")
    print(f"    '{order[:55]}'")
    if cost: print(f"    cost={cost}")
    if effects: print(f"    effects={effects}")
    if acts: print(f"    actions={acts}")
    err = r.get('action_errors') or r.get('error')
    if err: print(f"    errors={err}")
    nar = r.get('narrative', '')[:80]
    if nar: print(f"    → {nar}")

print(f"\n{'='*60}")
print(f"  Passed: {passed}/{len(tests)}")
print(f"{'='*60}")
