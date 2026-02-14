create table permissions (
  id text primary key,       -- e.g. UNIVERSE:READ
  resource_type text not null,
  action text not null,
  name text not null,
  description text,
  permission_valid_on_resource boolean not null default false,
  scope text not null,
  effect text not null,
  prerequisites jsonb not null default '[]',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_permissions_resource_action ON permissions(resource_type, action);