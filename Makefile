up:
	docker compose up -d

down:
	docker compose down

up-db:
	docker compose up -d postgres-1 postgres-2 postgres-3

down-db:
	docker compose down
