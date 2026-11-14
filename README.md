# Life Coach (Phase 1)

Tumhare apne phone ke liye personal assistant app. Internet permission **nahi** hai, saara data sirf phone me rehta hai.

## Kya hai Phase 1 me
- Steps counter (background me chalta hai) + roz ka target (6,000 se shuru, har hafte +1,000, max 10,000)
- Raat ko lock: 10 PM tak steps poore nahi hue to phone lock (sirf call + Emergency unlock khule). Office time (9 AM - 8 PM) me kabhi lock nahi.
- Reminders: subah walk, girlfriend ko good morning / good afternoon, har ghante khade hone ka, dinner, steps warning, sone ka time, Sunday ko weight
- Girlfriend ko WhatsApp message: notification ya Home ke button se chat khulti hai aur message apne aap send ho jata hai
- Khana (veg, Indian) + paani + weight log, calories aur protein ka hisab

## Build kaise karein
1. Android Studio kholo -> **Open** -> is `LifeCoach` folder ko choose karo.
2. Gradle sync hone do (pehli baar 5-10 minute lag sakte hain, internet chahiye).
3. Phone me **Developer options -> USB debugging** ON karo, USB se connect karo.
4. Upar **Run** (green play) dabao.

## Phone me setup (ek baar)
1. App kholo -> Home pe "Setup baaki hai" card ke buttons se permissions do.
2. **Accessibility**: agar ON nahi ho paa raha (grey hai), App info -> upar ⋮ -> **Allow restricted settings**, phir Accessibility me "Life Coach" ON karo.
3. Settings tab me **Emergency passcode** (8+ characters) set karo. Iske bina lock ON nahi hota.
4. Settings tab me girlfriend ka WhatsApp number daalo.
5. **Lock test (60 sec)** dabake check karo ki lock aur Emergency unlock kaam kar rahe hain.

## Dhyan rakho
- Shaam 6 baje ke baad steps/lock ki settings badal nahi sakte.
- Lock me call khuli rehti hai (dialer + incoming call).
- WhatsApp auto-send WhatsApp ke "Send" button ko dhoondta hai. WhatsApp update ke baad kabhi band ho sakta hai, tab bas Send khud dabana padega.
