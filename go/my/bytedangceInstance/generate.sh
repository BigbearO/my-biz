#!/usr/bin/env bash

 project_module="bytedangceInstance"
 echo "project_module=$project_module"
 dir=$1
 echo "dir=$dir"
 mkdir $dir && cd $dir
 kitex -module $project_module  -service user ../idl/user.thrift
 cd ..