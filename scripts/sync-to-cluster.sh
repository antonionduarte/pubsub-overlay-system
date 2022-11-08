#!/usr/bin/bash

if [ $# -ne 1 ]; then
    echo "Usage: $0 <cluster ssh host>"
    exit 1
fi

rsync -avz --progress ./ $1:./asd-project1