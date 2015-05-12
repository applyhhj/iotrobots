import paramiko
import time

ipNz = '10.23.0.92'
ipB = '10.23.1.192'
ipI = '10.23.1.190'
resultsdir = ""

sshNZ = paramiko.SSHClient()
sshNZ.set_missing_host_key_policy(paramiko.AutoAddPolicy())
sshNZ.connect(ipNz, username='ubuntu', key_filename='/home/ubuntu/skamburu-key')

sshBR = paramiko.SSHClient()
sshBR.set_missing_host_key_policy(paramiko.AutoAddPolicy())
sshBR.connect(ipB, username='ubuntu', key_filename='/home/ubuntu/skamburu-key')

sshI = paramiko.SSHClient()
sshI.set_missing_host_key_policy(paramiko.AutoAddPolicy())
sshI.connect(ipI, username='ubuntu', key_filename='/home/ubuntu/skamburu-key')

sshIR = paramiko.SSHClient()
sshIR.set_missing_host_key_policy(paramiko.AutoAddPolicy())
sshIR.connect(ipI, username='ubuntu', key_filename='/home/ubuntu/skamburu-key')

def copy_scan_matcher(ssh, topologyFile):
    print "compiling"
    cmd = 'cp ../simulator/testscript/' + str(topologyFile) + ' serial/src/main/java/cgl/iotrobots/slam/core/scanmatcher/ScanMatcher.java'
    channel = ssh.invoke_shell()
    stdin = channel.makefile('wb')
    stdout = channel.makefile('rb')
    stdin.write('''
    cd /home/ubuntu/projects/iotrobots/slam
    ''' + cmd + '''
    exit
    ''')
    print stdout.read()
    stdout.close()
    stdin.close()

def compile_program(ssh, topologyFile):
    print "compiling"
    cmd = 'cp test/' + str(topologyFile) + ' src/main/resources/topology.yaml'
    channel = ssh.invoke_shell()
    stdin = channel.makefile('wb')
    stdout = channel.makefile('rb')
    stdin.write('''
    cd /home/ubuntu/projects/iotrobots/slam
    ''' + cmd + '''
    mvn clean install -Dmaven.test.skip=true
    exit
    ''')
    print stdout.read()
    stdout.close()
    stdin.close()

def restart_zk(ssh):
    print "compiling"
    channel = ssh.invoke_shell()
    stdin = channel.makefile('wb')
    stdout = channel.makefile('rb')
    stdin.write('''
    sudo service supervisor stop
    sleep 5
    rm -rf /tmp/zookeeper/
    sudo service supervisor start
    sleep 60
    exit
    ''')
    print stdout.read()
    stdout.close()
    stdin.close()

def exec_storm(ssh, parallel):
    print "executing storm commands"
    cmd = './bin/storm jar ~/projects/iotrobots/collectives/target/collectives-1.0-SNAPSHOT-jar-with-dependencies.jar edu.iu.cs.storm.collectives.app.BroadCastTopology  -name bcast_processor -ds_mode 0 -p ' + str(parallel)
    channel = ssh.invoke_shell()
    stdin = channel.makefile('wb')
    stdout = channel.makefile('rb')
    stdin.write('''
    cd deploy/storm
    ./bin/storm kill bcast_processor -w 1
    sleep 30
    ''' + cmd + '''
    exit
    ''')
    print stdout.read()
    stdout.close()
    stdin.close()
    time.sleep(15)

def exec_rabbit(ssh):
    print "starting rabbitmq...."
    channel = ssh.invoke_shell()
    stdin = channel.makefile('wb')
    stdout = channel.makefile('rb')
    stdin.write('''
    pid=`ps ax | grep "rabbitmq" | awk '{print $1}'`
    sudo kill $pid
    sleep 5
    cd /home/ubuntu/deploy/rabbitmq_server-3.3.2/sbin
    sudo ./rabbitmq-server > /dev/null 2>&1 &
    exit
    ''')
    print stdout.read()
    stdout.close()
    stdin.close()
    time.sleep(5)

def run_test(ssh, test, parallel, t, size):
    print "running test...."
    cmd = 'java -cp target/collectives-1.0-SNAPSHOT-jar-with-dependencies.jar edu.iu.cs.storm.collectives.app.BroadCastTopology "amqp://' + str(ipB) +':5672"' + ' ' + str(test) + '/' + str(size) + '_' + str(parallel) + ' ' + str(size) + ' ' + str(t)
    channel = ssh.invoke_shell()
    stdin = channel.makefile('wb')
    stdout = channel.makefile('rb')
    stdin.write('''
    cd /home/ubuntu/projects/iotrobots/collectives
    ''' + cmd + '''
    exit
    ''')
    print stdout.read()
    stdout.close()
    stdin.close()

def run_bcast_test():
    tasks = [2, 4, 6, 8, 10, 12, 14, 16, 18, 20]
    data = [100, 1000, 10000, 100000, 20000, 30000, 40000, 50000, 60000, 70000, 80000, 90000, 100000]

    compile_program(sshNZ, "test.yaml")
    for t in tasks:
        exec_storm(sshNZ, t)
        for d in data:
            exec_rabbit(sshBR)
            run_test(sshIR, 'bcast', t, 100, d)

def start_cluster(par, t):
    exec_rabbit(sshBR)
    exec_storm(sshNZ, par, t)

def copy_file(ssh, src, dest):
    print "copy file"
    cmd = 'cp ' + str(src) + ' ' + str(dest)
    channel = ssh.invoke_shell()
    stdin = channel.makefile('wb')
    stdout = channel.makefile('rb')
    stdin.write('''
    cd /home/ubuntu/projects/iotrobots/collectives
    ''' + cmd + '''
    exit
    ''')
    print stdout.read()
    stdout.close()
    stdin.close()

def compile(ssh, dir):
    print "compiling"
    cmd = 'cd ' + str(dir)
    channel = ssh.invoke_shell()
    stdin = channel.makefile('wb')
    stdout = channel.makefile('rb')
    stdin.write('''
    ''' + cmd + '''
    mvn clean install -Dmaven.test.skip=true
    exit
    ''')
    print stdout.read()
    stdout.close()
    stdin.close()

def main():
    restart_zk(sshNZ)
    run_bcast_test()


if __name__ == "__main__":
    main()

