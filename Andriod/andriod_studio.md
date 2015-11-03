/* @[]: represent system cmd or system file*/

Android_studio settup:

[option 1]
    .Download the andriod_studio.zip and jdk1.7.0_71
    .unzip both of them under the XXX floder
    .[vim ~/.bashrc];
        add like:
            export JAVA_HOME=/home/sherlock/root/jdk1.7.0_71
            export JDK_HOME=/home/sherlock/root/jdk1.7.0_71
            export PATH=$PATH:/home/sherlock/root/jdk1.7.0_71/bin
    .[cd XXX/andrid_studio/bin]; run: ./studio.sh
    .When the program running, if neccssary, modify the proxy config:
            [vim ~/.AndroidXXX/config/options/proxy.settings.xml]
    .Then the program will download some necesssary SDK

[option 2]
    .Copy others config file:[~/.AndroidXX],[~/.android],[XXXX/android_studio],[XXXX/jdk1.7.0_71],[~/Android/SKD]


