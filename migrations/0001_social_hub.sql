CREATE TABLE IF NOT EXISTS social_connections (
  id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  provider_account_id TEXT NOT NULL,
  display_name TEXT NOT NULL DEFAULT '',
  encrypted_access_token TEXT NOT NULL,
  encrypted_refresh_token TEXT,
  token_expires_at INTEGER,
  granted_scopes TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'connected',
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  UNIQUE(owner_id, provider, provider_account_id)
);

CREATE INDEX IF NOT EXISTS idx_social_connections_owner
  ON social_connections(owner_id, provider);

CREATE TABLE IF NOT EXISTS social_oauth_states (
  state_hash TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL,
  provider TEXT NOT NULL,
  encrypted_pkce_verifier TEXT,
  redirect_uri TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_social_oauth_states_expiry
  ON social_oauth_states(expires_at);
