FROM flink:1.15.4

ARG PYTHON_VERSION=3.8.9

RUN apt-get update -y && \
apt-get install -y build-essential libssl-dev zlib1g-dev libbz2-dev libffi-dev && \
wget https://www.python.org/ftp/python/${PYTHON_VERSION}/Python-${PYTHON_VERSION}.tgz && \
tar -xvf Python-${PYTHON_VERSION}.tgz && \
cd Python-${PYTHON_VERSION} && \
./configure --without-tests --enable-shared && \
make -j6 && \
make install && \
ldconfig /usr/local/lib && \
cd .. && rm -f Python-${PYTHON_VERSION}.tgz && rm -rf Python-${PYTHON_VERSION} && \
ln -s /usr/local/bin/python3 /usr/local/bin/python && \
apt-get clean && \
rm -rf /var/lib/apt/lists/*

# install PyFlink, boto3 and AWS CLI
RUN pip3 install apache-flink==1.15.4 boto3 awscli
