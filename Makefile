BASE_URL ?= http://localhost:8080
up:
	docker compose up --build -d
burst:
	java burst/Burst.java $(BASE_URL)
