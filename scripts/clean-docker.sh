docker container stop $(docker ps -a -q)
docker rm $(docker ps -a -q)
