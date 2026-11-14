import { timingSafeEqual } from "node:crypto";

export const DEFAULT_SYSTEM_PROMPT =
  "أنت صديقي، المساعد الرقمي الشخصي لعزام. أجب بالعربية، واستخدم اللهجة اليمنية بصورة طبيعية عندما تلائم السياق. كن عملياً ودقيقاً وصريحاً، ولا تدّع تنفيذ إجراء لم تنفذه. اطلب موافقة عزام قبل أي إجراء حساس أو شراء أو إرسال أو حذف. احترم الخصوصية ولا تطلب كلمات المرور أو مفاتيح API. وضّح عدم اليقين واستشهد بالمصادر عند استخدام البحث.";

export function json(res, status, data, headers = {}) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Content-Type-Options", "nosniff");
  for (const [key, value] of Object.entries(headers)) res.setHeader(key, value);
  res.end(JSON.stringify(data));
}

export function cors(req, res) {
  const origin = req.headers.origin;
  res.setHeader("Vary", "Origin");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  if (process.env.ALLOWED_ORIGIN && origin === process.env.ALLOWED_ORIGIN) {
    res.setHeader("Access-Control-Allow-Origin", origin);
  }
  if (req.method === "OPTIONS") {
    if (process.env.ALLOWED_ORIGIN && origin !== process.env.ALLOWED_ORIGIN) {
      json(res, 403, { error: "Origin not allowed." });
    } else {
      res.statusCode = 204;
      res.end();
    }
    return true;
  }
  return false;
}

export function safeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && timingSafeEqual(left, right);
}

export function validateInput(input) {
  if (!Array.isArray(input) || input.length < 1 || input.length > 24) {
    return "input must contain 1 to 24 turns.";
  }
  for (const turn of input) {
    if (!turn || !["user", "assistant"].includes(turn.role)) return "Invalid role.";
    if (typeof turn.content === "string") {
      if (turn.content.length > 16000) return "Message too large.";
      continue;
    }
    if (!Array.isArray(turn.content) || turn.content.length < 1 || turn.content.length > 4) {
      return "Invalid content.";
    }
    for (const part of turn.content) {
      if (!part || !["input_text", "input_image"].includes(part.type)) return "Invalid content part.";
      if (part.type === "input_text" && (typeof part.text !== "string" || part.text.length > 16000)) {
        return "Invalid input text.";
      }
      if (part.type === "input_image" && (typeof part.image_url !== "string" ||
          !(part.image_url.startsWith("https://") || part.image_url.startsWith("data:image/")))) {
        return "Invalid image input.";
      }
    }
  }
  return null;
}
