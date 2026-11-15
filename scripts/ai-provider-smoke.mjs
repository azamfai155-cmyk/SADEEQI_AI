// Run with AI_PROVIDER=groq GROQ_API_KEY=... npm run smoke:ai
// Makes one short live request. The API key and response body are never printed.
const provider = (process.env.AI_PROVIDER || (process.env.GROQ_API_KEY ? 'groq' : 'openai')).toLowerCase();
const model = provider === 'groq'
  ? (process.env.GROQ_MODEL || 'openai/gpt-oss-20b')
  : (process.env.OPENAI_MODEL || 'gpt-4.1-mini');
const apiKey = provider === 'groq' ? process.env.GROQ_API_KEY : process.env.OPENAI_API_KEY;
if (!['groq', 'openai'].includes(provider)) throw new Error('AI_PROVIDER must be groq or openai.');
if (!apiKey) throw new Error(`Missing API key for ${provider}.`);
const url = provider === 'groq'
  ? 'https://api.groq.com/openai/v1/responses'
  : 'https://api.openai.com/v1/responses';
const payload = {
  model,
  instructions: 'Reply with exactly: SADEEQI_AI_PROVIDER_OK',
  input: 'Return the exact confirmation string now.'
};
if (provider === 'openai') payload.store = false;
const response = await fetch(url, {
  method: 'POST',
  headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
  body: JSON.stringify(payload),
  signal: AbortSignal.timeout(30000)
});
let data;
try { data = await response.json(); }
catch { throw new Error(`Provider returned invalid JSON (HTTP ${response.status}).`); }
if (!response.ok) {
  const message = data?.error?.message || `HTTP ${response.status}`;
  throw new Error(`${provider} live request failed: ${message}`);
}
const output = typeof data.output_text === 'string' ? data.output_text :
  (data.output || []).flatMap(item => item.content || [])
    .filter(part => part.type === 'output_text').map(part => part.text).join('\n');
if (!output.includes('SADEEQI_AI_PROVIDER_OK')) throw new Error('Provider returned an unexpected response.');
console.log(`PASS: live ${provider} Responses API call succeeded (model: ${model}).`);
