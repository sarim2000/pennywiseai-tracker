#!/usr/bin/env python3
"""Spike: turn spoken Arabic/Hindi/English number words into digits before the model."""
import json
import re
import sys

AR = {"صفر": 0, "واحد": 1, "وحدة": 1, "واحدة": 1, "اثنين": 2, "اثنان": 2, "ثنين": 2, "تنين": 2, "ثلاثة": 3, "ثلاث": 3, "تلاتة": 3, "اربعة": 4, "أربعة": 4, "اربع": 4, "أربع": 4, "خمسة": 5, "خمس": 5, "ستة": 6, "ست": 6, "سبعة": 7, "سبع": 7, "ثمانية": 8, "ثماني": 8, "تمانية": 8, "تسعة": 9, "تسع": 9, "عشرة": 10, "عشر": 10,
      "احدى عشر": 11, "احد عشر": 11, "أحد عشر": 11, "اثنا عشر": 12, "اثني عشر": 12, "اتناشر": 12, "ثلاثة عشر": 13, "ثلاث عشرة": 13, "اربعة عشر": 14, "أربعة عشر": 14, "خمسة عشر": 15, "خمس عشرة": 15, "خمستعش": 15, "ستة عشر": 16, "سبعة عشر": 17, "ثمانية عشر": 18, "تسعة عشر": 19,
      "عشرين": 20, "عشرون": 20, "ثلاثين": 30, "ثلاثون": 30, "تلاتين": 30, "اربعين": 40, "أربعين": 40, "خمسين": 50, "ستين": 60, "سبعين": 70, "ثمانين": 80, "تمانين": 80, "تسعين": 90}
AR_HUNDREDS = {"مية": 100, "مئة": 100, "مائة": 100, "ميتين": 200, "مئتين": 200, "مائتين": 200, "ثلاثمية": 300, "ثلاثمائة": 300, "تلتمية": 300, "اربعمية": 400, "أربعمية": 400, "اربعمائة": 400, "أربعمائة": 400, "خمسمية": 500, "خمسمائة": 500, "ستمية": 600, "ستمائة": 600, "سبعمية": 700, "سبعمائة": 700, "ثمانمية": 800, "ثمانمائة": 800, "تسعمية": 900, "تسعمائة": 900}
AR_SCALE = {"الف": 1000, "ألف": 1000, "الفين": 2000, "ألفين": 2000, "مليون": 1000000}

HI = dict(zip("एक दो तीन चार पांच छह सात आठ नौ दस ग्यारह बारह तेरह चौदह पंद्रह सोलह सत्रह अठारह उन्नीस बीस इक्कीस बाईस तेईस चौबीस पच्चीस छब्बीस सत्ताईस अट्ठाईस उनतीस तीस इकतीस बत्तीस तैंतीस चौंतीस पैंतीस छत्तीस सैंतीस अड़तीस उनतालीस चालीस इकतालीस बयालीस तैंतालीस चौवालीस पैंतालीस छियालीस सैंतालीस अड़तालीस उनचास पचास इक्यावन बावन तिरपन चौवन पचपन छप्पन सत्तावन अट्ठावन उनसठ साठ इकसठ बासठ तिरसठ चौंसठ पैंसठ छियासठ सड़सठ अड़सठ उनहत्तर सत्तर इकहत्तर बहत्तर तिहत्तर चौहत्तर पचहत्तर छिहत्तर सतहत्तर अठहत्तर उन्यासी अस्सी इक्यासी बयासी तिरासी चौरासी पचासी छियासी सत्तासी अट्ठासी नवासी नब्बे इक्यानवे बानवे तिरानवे चौरानवे पचानवे छियानवे सत्तानवे अट्ठानवे निन्यानवे".split(), range(1, 100)))
HI["पाँच"] = 5
HI_LATIN = {"ek": 1, "do": 2, "teen": 3, "char": 4, "paanch": 5, "chhah": 6, "saat": 7, "aath": 8, "nau": 9, "das": 10, "bees": 20, "pachis": 25, "tees": 30, "chalis": 40, "pachas": 50, "saath": 60, "sattar": 70, "assi": 80, "nabbe": 90}
HI_MULT = {"सौ": 100, "हज़ार": 1000, "हजार": 1000, "लाख": 100000, "sau": 100, "hazar": 1000, "lakh": 100000}
HI_FRAC = {"डेढ़": 1.5, "ढाई": 2.5, "dedh": 1.5, "dhai": 2.5}
SADHE = {"साढ़े", "saadhe", "sadhe"}


def norm_ar(text):
    words = text.split()
    out, i = [], 0

    def value(raw):
        w = raw[1:] if raw.startswith("و") and len(raw) > 2 and (raw[1:] in AR or raw[1:] in AR_HUNDREDS or raw[1:] in AR_SCALE) else raw
        w = w[2:] if w.startswith("بـ") else w[1:] if w.startswith("ب") and w[1:] in {**AR, **AR_HUNDREDS, **AR_SCALE} else w
        return w

    while i < len(words):
        total, cur, j, prefix = 0, 0, i, ""
        while j < len(words):
            w = value(words[j].strip("،,.؟"))
            pair = (w + " " + value(words[j + 1].strip("،,.؟"))) if j + 1 < len(words) else ""
            if pair in AR:
                cur += AR[pair]; j += 2
            elif w in AR and not (j == i and w in ("خمس", "ست", "سبع", "تسع", "عشر") and j + 1 == len(words)):
                cur += AR[w]; j += 1
            elif w in AR_HUNDREDS:
                cur += AR_HUNDREDS[w]; j += 1
            elif w in AR_SCALE:
                s = AR_SCALE[w]
                total += (cur or 1) * s if s in (1000, 1000000) and w not in ("الفين", "ألفين") else s
                cur = 0; j += 1
            else:
                break
        if j > i:
            out.append(str(total + cur)); i = j
        else:
            out.append(words[i]); i += 1
    return " ".join(out)


def norm_hi(text):
    words = re.findall(r"[^\s,،]+|,", text)
    out, i = [], 0
    while i < len(words):
        w = words[i].lower()
        val = None
        j = i
        if w in HI_FRAC and i + 1 < len(words) and words[i + 1].lower() in HI_MULT:
            val = int(HI_FRAC[w] * HI_MULT[words[i + 1].lower()]); j = i + 2
        elif w in SADHE and i + 2 < len(words) and (words[i + 1] in HI or words[i + 1].lower() in HI_LATIN) and words[i + 2].lower() in HI_MULT:
            n = HI.get(words[i + 1], HI_LATIN.get(words[i + 1].lower()))
            val = int((n + 0.5) * HI_MULT[words[i + 2].lower()]); j = i + 3
        else:
            total, cur, k = 0, 0, i
            while k < len(words):
                x = words[k].lower()
                n = HI.get(words[k], HI_LATIN.get(x))
                if n is not None:
                    cur += n; k += 1
                elif x in HI_MULT and k > i:
                    m = HI_MULT[x]
                    if m == 100:
                        cur *= 100
                    else:
                        total += cur * m; cur = 0
                    k += 1
                elif x in HI_MULT and k == i and m_is_number_context(words, k):
                    cur = HI_MULT[x]; k += 1
                else:
                    break
            if k > i:
                val = total + cur; j = k
        if val is not None:
            out.append(str(val)); i = j
        else:
            out.append(words[i]); i += 1
    return " ".join(out).replace(" ,", ",")


def m_is_number_context(words, k):
    return k + 1 < len(words) and words[k + 1] in ("रुपये", "रुपए", "का", "की", "ka", "ki")


def main():
    cases = json.load(open(sys.argv[1]))["cases"]
    out = []
    for c in cases:
        if c["lang"] == "en":
            continue
        fixed = norm_ar(c["text"]) if c["lang"] == "ar" else norm_hi(c["text"])
        out.append(dict(c, text=fixed, id=c["id"] + "__norm"))
        if "__ref__" in c["id"]:
            print(c["text"], "=>", fixed)
    json.dump({"cases": out}, open(sys.argv[2], "w"), ensure_ascii=False)
    print(len(out))


if __name__ == "__main__":
    main()
