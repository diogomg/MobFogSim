#!/bin/bash
mkdir -p out/colda out/coldb out/livea out/liveb
seeds=(290538 909538 310420 793815 299994 201337 768912 768794 148536 890885 852888 365080 233377 225574 828104 676239 522308 66444 611580 729303 199643 117189 899336 361201 394590 899195 500091 631323 285948 4096 995694 589703 5962 830047 319530 557745 652202 553793 884108 898233 996114 497459 146660 49340 6275 168584 431307 327829 486411 85471 789034 886174 968871 888121 302512 254922 602451 86904 969585 930192 139635 472927 141124 850105 71061 708653 377443 170019 781763 386778 838841 607262 186636 680028 23352 478953 410940 457000 102207 592288 783673 766519 685743 820085 810388 444326 710863 524414 178585 409993 267901 178077 606969 266023 742696 202338 551856 167231 633980 236431 183083 167044 711361 774777 101751 471745 962561 721789 468238 51584 539053 824392 943808 360657 43462 282109 943802 506506 14373 906566 257196 836491 224197 940687 19340 594793 839115 613522 64776 572741 957401 579560 339615 555208 571606 457749 217957 799681 372307 586066 67672 915919 563233 936719 905426 857775 174384 855637 634974 953845 971344 108067 822798 395147 212755 135205 790133 151078 143380 46085 365866 83170 383502 108009 130706 693250 613629 91630 434347 636327 918811 303947 68243 373805 900438 437029 829801 715801 279405 85264 217909 135618 889110 490622 800908 803175 783096 185894 345538 697000 706984 217143 577703 518875 653268 358970 600272 299742 589657 902667 733084 227017 850042 515443 198246 638972 581893 124601 331120 469858 615207 141050 785077 20016 365474 924065 96220 115890 785116 39286 395975 74823 464142 308476 787062 338866 810873 231183 16150 115415 449142 541462 114238 702837 634219 629190 321030 54329 658876 967591 504623 614945 665802 437262 838537 212044 642414 441045 556536 589930 330049 307462 856274 990528 672442 647615 536273 270979 595527 764849 166899 833337 371782 277162 815544 906053 441601 404889 871468 484543 284546 343332 638091 182112 875745 425341 672719 20038 598148 848318 885988 172911 856550 646589 920965 931216 244624 380601 556420 595907 200968 100958 269133 316599 570498 478940 536387 276533 667100 123021 87336 939771 689030 199129 727197 409913 672987 573655 460261 816109 334699 141529 93655 702653 320051 966252 18352 390048 955240 948251 288592 470703 665257 438907 794032 803464 25141 319365 675498 976109 845942 231691 466852 645130 796136 2304 180490 702051 94966 931612 230507 753811 459126 57849 125525 332304 669509 175089 100832 115338 841308 234111 152693 439972 749675 864037 783804 260067 273759 790307 522406 204180 553243 87052 203392 694123 164289 622050 493310 378943 764419 674309 172360 452849 864852 208674 189890 609731 887767 744617 445507 534142 422463 392491 205863 757740 275475 138134 759033 219295 645355 346135 919209 559675 501535 239987 475444 441807 154091 315979 740936 501044 577059 815773 901466 530897 760889 456222 492805 372256 201136 237868 951292 387701 952541 354789 745695 574531 87261 157850 843291 351040 202411 691554 197281 976880 656978 684734 870789 258606 998861 682967 865034 438844 380707 185229 179516 818832 313074 168819 894824 458587 781278 307967 901123 192512 384295 282855 520387 592846 834807 640512 760246 622454 931682 587376 697400 697369 995317 489044 22887 70466 253234 332920 970936 63586 339358 699236 581115 312278 109313 725552 765431 181807 189325 124390 811639 141751 974106 52236 301840 646171 402022 13957 977440 831990 899674 388878 535770 266302 769209 116823 116852 129408 965468 331606)
i=0
for inp in input/*
do
	seed=${seeds[i]}
	filename=$(basename "$inp")
#	mkdir -p ~/SIMPAT/speed1x

	for predict in 0
	do
		mkdir ${filename%.*}
		/usr/lib/jvm/java-8-oracle/bin/java -Xmx10g -Dfile.encoding=UTF-8 -classpath bin:jars/cloudsim-3.0.3-sources.jar:jars/cloudsim-3.0.3.jar:jars/cloudsim-examples-3.0.3-sources.jar:jars/cloudsim-examples-3.0.3.jar:jars/commons-math3-3.5/commons-math3-3.5.jar:jars/guava-18.0.jar:jars/json-simple-1.1.1.jar:jars/junit.jar:jars/org.hamcrest.core_1.3.0.v201303031735.jar org.fog.vmmobile.AppExemplo2 1 $seed 0 0 1 11 0 $predict 0 61
#		touch a.txt
		rm out.txt 0out.txt
		mv *.txt ${filename%.*}
		mkdir -p out/colda/$predict
		mv ${filename%.*} out/colda/$predict

        	mkdir ${filename%.*}
	        /usr/lib/jvm/java-8-oracle/bin/java -Xmx10g -Dfile.encoding=UTF-8 -classpath bin:jars/cloudsim-3.0.3-sources.jar:jars/cloudsim-3.0.3.jar:jars/cloudsim-examples-3.0.3-sources.jar:jars/cloudsim-examples-3.0.3.jar:jars/commons-math3-3.5/commons-math3-3.5.jar:jars/guava-18.0.jar:jars/json-simple-1.1.1.jar:jars/junit.jar:jars/org.hamcrest.core_1.3.0.v201303031735.jar org.fog.vmmobile.AppExemplo2 1 $seed 0 0 1 74 0 $predict 0 3
		rm out.txt 0out.txt
#		touch a.txt
	        mv *.txt ${filename%.*}
		mkdir -p out/coldb/$predict
        	mv ${filename%.*} out/coldb/$predict

#	        mkdir ${filename%.*}
#	        /usr/lib/jvm/java-8-oracle/bin/java -Xmx10g -Dfile.encoding=UTF-8 -classpath bin:jars/cloudsim-3.0.3-sources.jar:jars/cloudsim-3.0.3.jar:jars/cloudsim-examples-3.0.3-sources.jar:jars/cloudsim-examples-3.0.3.jar:jars/commons-math3-3.5/commons-math3-3.5.jar:jars/guava-18.0.jar:jars/json-simple-1.1.1.jar:jars/junit.jar:jars/org.hamcrest.core_1.3.0.v201303031735.jar org.fog.vmmobile.AppExemplo2 1 $seed 0 0 1 11 1 $predict 0 61
#		rm out.txt 0out.txt
#		touch a.txt
#	        mv *.txt ${filename%.*}
#		mkdir -p out/dockera/$predict
#	        mv ${filename%.*} out/dockera/$predict

#	        mkdir ${filename%.*}
#	        /usr/lib/jvm/java-8-oracle/bin/java -Xmx10g -Dfile.encoding=UTF-8 -classpath bin:jars/cloudsim-3.0.3-sources.jar:jars/cloudsim-3.0.3.jar:jars/cloudsim-examples-3.0.3-sources.jar:jars/cloudsim-examples-3.0.3.jar:jars/commons-math3-3.5/commons-math3-3.5.jar:jars/guava-18.0.jar:jars/json-simple-1.1.1.jar:jars/junit.jar:jars/org.hamcrest.core_1.3.0.v201303031735.jar org.fog.vmmobile.AppExemplo2 1 $seed 0 0 1 74 1 $predict 0 3
#		rm out.txt 0out.txt
#		touch a.txt
#	        mv *.txt ${filename%.*}
#		mkdir -p out/dockerb/$predict
#	        mv ${filename%.*} out/dockerb/$predict

	        mkdir ${filename%.*}
	        /usr/lib/jvm/java-8-oracle/bin/java -Xmx10g -Dfile.encoding=UTF-8 -classpath bin:jars/cloudsim-3.0.3-sources.jar:jars/cloudsim-3.0.3.jar:jars/cloudsim-examples-3.0.3-sources.jar:jars/cloudsim-examples-3.0.3.jar:jars/commons-math3-3.5/commons-math3-3.5.jar:jars/guava-18.0.jar:jars/json-simple-1.1.1.jar:jars/junit.jar:jars/org.hamcrest.core_1.3.0.v201303031735.jar org.fog.vmmobile.AppExemplo2 1 $seed 0 0 1 11 2 $predict 0 61
		rm out.txt 0out.txt
#		touch a.txt
	        mv *.txt ${filename%.*}
		mkdir -p out/livea/$predict
	        mv ${filename%.*} out/livea/$predict

	        mkdir ${filename%.*}
	        /usr/lib/jvm/java-8-oracle/bin/java -Xmx10g -Dfile.encoding=UTF-8 -classpath bin:jars/cloudsim-3.0.3-sources.jar:jars/cloudsim-3.0.3.jar:jars/cloudsim-examples-3.0.3-sources.jar:jars/cloudsim-examples-3.0.3.jar:jars/commons-math3-3.5/commons-math3-3.5.jar:jars/guava-18.0.jar:jars/json-simple-1.1.1.jar:jars/junit.jar:jars/org.hamcrest.core_1.3.0.v201303031735.jar org.fog.vmmobile.AppExemplo2 1 $seed 0 0 1 74 2 $predict 0 3
		rm out.txt 0out.txt
#		touch a.txt
        	mv *.txt ${filename%.*}
		mkdir -p out/liveb/$predict
	        mv ${filename%.*} out/liveb/$predict

#		cp -r out/* ~/SIMPAT/speed1x
	done
	((i++))
	mv input/$filename jaexecutados
done

