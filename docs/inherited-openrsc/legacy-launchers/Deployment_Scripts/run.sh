#!/bin/sh

# Run the game server in a detached screen
echo ""
echo "Launching the game server in a new screen."
echo ""
echo "Type 'screen -r' followed by the name of the server to access the game server console."
echo "Press 'CTRL + C' to shut down the server from that screen or"
echo "Use 'CTRL + A, D' to detach the live server screen so it runs in the background."
echo ""
cd server

# runs server with the configuration found in "server/local.conf"
sh ./ant_launcher.sh local g1gc

# uncomment to run additional servers with different configurations. Prod use: uncomment all below
#sh ./ant_launcher.sh openrsc &&  \
#sh ./ant_launcher.sh rsccabbage && \
#sh ./ant_launcher.sh uranium && \
#sh ./ant_launcher.sh rsccoleslaw && \
#sh ./ant_launcher.sh 2001scape && \
##sh ./ant_launcher.sh openpk
