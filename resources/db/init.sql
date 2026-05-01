CREATE TABLE
  IF NOT EXISTS partners (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    cnpj VARCHAR(14) NOT NULL UNIQUE,
    api_key UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW ()
  );

CREATE TABLE
  IF NOT EXISTS quotes (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners (id),
    age INTEGER NOT NULL,
    gender CHAR(1) NOT NULL,
    price VARCHAR(50) NOT NULL,
    expire_at DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW ()
  );

CREATE TABLE
  IF NOT EXISTS policies (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners (id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW ()
  );

