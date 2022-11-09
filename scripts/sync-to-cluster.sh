#!/usr/bin/bash

if [ $# -ne 1 ]; then
    echo "Usage: $0 <cluster ssh host>"
    exit 1
fi

rsync -avzq --exclude analysis/metrics --exclude analysis/experiments ./ $1:./asd-project1