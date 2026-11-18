import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const team = JSON.parse(await readFile(new URL('./team.json', import.meta.url), 'utf8'));

test('agency roster contains exactly twelve uniquely identified employees', () => {
  assert.equal(team.employees.length, 12);
  assert.equal(new Set(team.employees.map(agent => agent.id)).size, 12);
});

test('every employee has a distinct role, mission, voice profile and permission boundary', () => {
  for (const agent of team.employees) {
    assert.ok(agent.name);
    assert.ok(agent.title);
    assert.ok(agent.department);
    assert.ok(agent.mission);
    assert.ok(agent.voice?.locale);
    assert.ok(Number.isFinite(agent.voice.rate));
    assert.ok(Number.isFinite(agent.voice.pitch));
    assert.ok(Array.isArray(agent.permissions));
    assert.ok(Array.isArray(agent.approval_required));
    assert.ok(agent.approval_required.length > 0);
  }
  assert.equal(new Set(team.employees.map(agent => agent.title)).size, 12);
});

test('external actions are approval-gated in the roster policy', () => {
  const approvals = new Set(team.employees.flatMap(agent => agent.approval_required));
  for (const action of ['send_message', 'publish_content', 'spend_money', 'contract', 'payment']) {
    assert.ok(approvals.has(action), 'missing approval rule: ' + action);
  }
});
