#!/usr/bin/env bash

export DIR="/root/keytab-retriever"

echo "*** Starting to launch program ***"

    cd $DIR

echo "Launching jar via java command"

    java -jar keytab-retriever.jar $@

    sleep 1

echo "*** Finished program ***"