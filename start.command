#!/bin/bash
cd "$(dirname "$0")" || exit 1

pause_and_exit() { echo; read -r -p "Нажмите Enter, чтобы закрыть окно..." _; exit "${1:-1}"; }

if ! command -v docker >/dev/null 2>&1; then
  echo "Команда docker не найдена. Установите Docker Desktop и запустите скрипт снова."; pause_and_exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Запускаю Docker Desktop и жду, пока он будет готов (до 3 минут)..."
  open -a Docker
  for _ in $(seq 1 60); do docker info >/dev/null 2>&1 && break; sleep 3; done
  docker info >/dev/null 2>&1 || { echo "Docker не запустился. Откройте Docker Desktop, пройдите первый запуск и повторите."; pause_and_exit 1; }
fi

if [ ! -f .env ]; then cp .env.example .env; echo "Создан .env из шаблона: замените пароли в .env и перезапустите."; pause_and_exit 1; fi

if [ -z "$JAVA_HOME" ] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi

echo "== 1/3. База данных (docker compose up) =="
docker compose up -d --wait || { echo "Не удалось поднять БД."; pause_and_exit 1; }

echo; echo "== 2/3. Тесты =="
./mvnw test || { echo; echo "Тесты не прошли (см. вывод выше)."; pause_and_exit 1; }

echo; echo "== 3/3. Приложение: http://localhost:8080 (остановка: Ctrl+C) =="
./mvnw spring-boot:run
pause_and_exit 0
