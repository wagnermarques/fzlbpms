#!/bin/bash

docker build -t ia-agent-python-sdk:0.0.1 .

docker run --rm ia-agent-python-sdk:0.0.1 

