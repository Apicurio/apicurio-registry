# docker build -t="apicurio/apicurio-docs-builder" --rm .
# winpty docker run '-v=C:\Users\ewittman\git\apicurio\apicurio.github.io\registry\docs:/antora-dist' -it apicurio/apicurio-docs-builder
# winpty docker run '-v=C:\Users\ewittman\git\apicurio\apicurio.github.io\registry\docs:/antora-dist' --env GIT_REPO=https://github.com/EricWittmann/apicurio-registry.git --env GIT_BRANCH=antora -it apicurio/apicurio-docs-builder
FROM centos:8

ENV GIT_REPO https://github.com/Apicurio/apicurio-registry.git
ENV GIT_BRANCH master
ENV GIT_PATH .antora
ENV PLAYBOOK local-antora-playbook.yml

VOLUME /antora-dist

RUN yum update -y && \
    yum install -y git &&  \
    yum install -y nodejs && \
    npm i -g @antora/cli@2.3 @antora/site-generator-default@2.3

ADD build.sh build.sh

CMD ./build.sh