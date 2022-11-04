rm metrics/*
docker container stop $(docker ps -a -q)
docker rm $(docker ps -a -q)
mvn clean compile assembly:single
python3 spawn_hyparview.py

