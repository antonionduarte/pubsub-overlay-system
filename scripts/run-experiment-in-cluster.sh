if [ $# -ne 4 ]; then
    echo "Usage: $0 <cluster ssh host> <job id> <machine name> <experiment name>"
    exit 1
fi

CLUSTER_HOST=$1
JOB_ID=$2
MACHINE_NAME=$3
EXPERIMENT_NAME=$4

rsync -avzq --exclude analysis/metrics --exclude analysis/experiments ./ $CLUSTER_HOST:./asd-project1
# Delete any previous results
ssh $CLUSTER_HOST "OAR_JOB_ID=$JOB_ID oarsh $MACHINE_NAME 'rm -rf asd-project1/analysis/experiments && sleep 5'"
ssh $CLUSTER_HOST "OAR_JOB_ID=$JOB_ID oarsh $MACHINE_NAME 'cd asd-project1 && scripts/run-experiment.sh $EXPERIMENT_NAME'"
ssh $CLUSTER_HOST "OAR_JOB_ID=$JOB_ID oarcp -r $MACHINE_NAME:./asd-project1/analysis/experiments asd-project1/analysis/"
rsync -avz --exclude 'OAR.*' --exclude '.*' --exclude analysis/metrics $CLUSTER_HOST:./asd-project1/analysis/experiments/ ./analysis/experiments/