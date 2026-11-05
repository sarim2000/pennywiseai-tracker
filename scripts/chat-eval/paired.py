import json,sys,math
sys.path.insert(0,'.')
import score
SP=sys.argv[1]
cases={c["id"]:c for f in ("cases.json","cases_heldout.json") for c in json.load(open(f))["cases"]}
def load(n):
    out={}
    for pre in ("q25_","held_q25_"):
        try:
            for l in open(f"{SP}/{pre}{n}.jsonl"):
                r=json.loads(l); out[r["id"],int(r["rep"])]=score.score_row(cases[r["id"]],r)
        except FileNotFoundError: pass
    return out
def mcnemar(a,b,key):
    ids=[i for i in a if i in b and key in a[i] and key in b[i]]
    x=sum(1 for i in ids if a[i][key] and not b[i][key]); y=sum(1 for i in ids if b[i][key] and not a[i][key])
    n=x+y
    p=1.0 if n==0 else min(1.0,2*sum(math.comb(n,k) for k in range(0,min(x,y)+1))/2**n)
    return len(ids),sum(1 for i in ids if a[i][key]),sum(1 for i in ids if b[i][key]),x,y,p
base=sys.argv[2]; a=load(base)
for other in sys.argv[3:]:
    b=load(other)
    for key in ("amounts","exact","strict","rawtype","post"):
        n,pa,pb,x,y,p=mcnemar(a,b,key)
        print(f"{base} vs {other:18} {key:8} n={n} {pa}/{n} vs {pb}/{n}  only-first={x} only-second={y} p={p:.3f}")
