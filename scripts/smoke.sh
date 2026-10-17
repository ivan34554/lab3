#!/bin/bash
BASE="${BASE:-http://localhost:8080}"

call() {
  echo; echo "### $1 $2"
  curl -s -X "$1" "$BASE$2" -H 'Content-Type: application/json' ${3:+-d "$3"} -w '\nHTTP %{http_code}\n'
}

call GET  /api/podcasts
call GET  /api/episodes/2
call POST /api/episodes/4/publish-now
call POST /api/audio-files/4/make-main
call POST /api/publication-slots/2/publish
call POST /api/publication-slots/2/cancel
call POST /api/episodes/5/publish-now
call POST /api/podcasts/1/archive
call POST /api/episodes '{"podcastId":1,"title":"Новый","durationSeconds":60}'
call POST /api/podcasts/1/restore
