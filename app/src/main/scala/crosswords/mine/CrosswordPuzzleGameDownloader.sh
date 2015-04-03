#!/bin/bash

# 1-8100 extra small
# 8101-16200 small
# 16201-24300 medium
# 24301-32400 large

# There exists 4 type of crossword and for each puzzle we need to download the solution too.
# Number of puzzles: 32400
# Total number of files: 64800

echo "Starting download"
for index in {1..8100}
do
	
	wget -O data/html/crosswordpuzzlegames_puzzle_$index.html --user-agent="Mozilla/5.0" http://www.crosswordpuzzlegames.com/puzzles/gt_$index.html
	wget -O data/html/crosswordpuzzlegames_solution_$index.html --user-agent="Mozilla/5.0" http://www.crosswordpuzzlegames.com/solutions/gt_$index.html
	
	sleep 1
	
	index1=$((index+8100))
	wget -O data/html/crosswordpuzzlegames_puzzle_$index1.html --user-agent="Mozilla/5.0" http://www.crosswordpuzzlegames.com/puzzles/gs_$index.html
	wget -O data/html/crosswordpuzzlegames_solution_$index1.html --user-agent="Mozilla/5.0" http://www.crosswordpuzzlegames.com/solutions/gs_$index.html
	
	sleep 1
	
	index2=$((index1+8100))
	wget -O data/html/crosswordpuzzlegames_puzzle_$index2.html --user-agent="Mozilla/5.0" http://www.crosswordpuzzlegames.com/puzzles/gm_$index.html
	wget -O data/html/crosswordpuzzlegames_solution_$index2.html --user-agent="Mozilla/5.0" http://www.crosswordpuzzlegames.com/solutions/gm_$index.html
	
	sleep 1
	
	index3=$((index2+8100))
	#echo "crosswordpuzzlegames_puzzle_$index3.html >> http://www.crosswordpuzzlegames.com/puzzles/gl_$index.html"
	wget -O data/html/crosswordpuzzlegames_puzzle_$index3.html --user-agent="Mozilla/5.0" http://www.crosswordpuzzlegames.com/puzzles/gl_$index.html
	wget -O data/html/crosswordpuzzlegames_solution_$index3.html --user-agent="Mozilla/5.0" http://www.crosswordpuzzlegames.com/solutions/gl_$index.html
	
	sleep 1
	
done
echo "Done"



