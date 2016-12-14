#!/bin/sh.exe
rm ./target/universal/stage/RUNNING_PID
activator clean stage
cp -r sqlite ./target/universal/stage/
./target/universal/stage/bin/amikoweb -Dplay.crypto.secret=c1cc1o
