# amiko-web
AmiKo auf dem Web mit Play 2.5.2
## Install Activator
```
https://www.lightbend.com/activator/download
wget downloads.typesafe.com/typesafe-activator/1.3.10/typesafe-activator-1.3.10.zip
Add it to your Path in .bashrc
export PATH=/usr/local/src/activator-dist-1.3.10/bin:$PATH
```
## Clone the Software
```
git clone https://github.com/zdavatz/amiko-web.git
```
## Update the Software
```
cd /usr/local/src/amiko-web
git pull
activator -java-home /usr/local/src/jdk1.8.0_92/
cd /var/www/amiko.oddb.org/
rm amikoweb-1.0-SNAPSHOT.zip
mv /usr/local/src/amiko-web/target/universal/amikoweb-1.0-SNAPSHOT.zip /var/www/amiko.oddb.org/
rm -r /var/www/amiko.oddb.org/amikoweb-1.0-SNAPSHOT
unzip amikoweb-1.0-SNAPSHOT.zip
cp -r /var/www/amiko.oddb.org/amikoweb-1.0-SNAPSHOT/* /var/www/amiko.oddb.org/
vim conf/application.conf
svc -h /service/amiko.oddb.org
```
## Update the database
```
cd /var/www/amiko.oddb.org/sqlite
rm amiko_db_full_idx_de.db
wget http://pillbox.oddb.org/amiko_db_full_idx_de.zip
unzip amiko_db_full_idx_de.zip
rm amiko_db_full_idx_de.zip
svc -h /service/amiko.oddb.org
```
## Setup Daemontools in /service/amiko.oddb.org
```
mkdir /var/www/amiko.oddb.org/svc/run
-> #!/bin/sh
-> exec /var/www/amiko.oddb.org/bin/amikoweb -java-home /usr/local/src/jdk1.8.0_92/
ln -s /var/www/amiko.oddb.org/svc/ /service/amiko.oddb.org
```
## Setup Daemontools logging
```
vim /var/www/amiko.oddb.org/svc/log/run
-> #!/bin/sh
-> exec multilog t ./main
```
## Setup Apache with amiko.oddb.org.conf
```
<VirtualHost *:80>
  ProxyPreserveHost On
  ServerName amiko.oddb.org
  ProxyPass  /excluded !
  ProxyPass / http://127.0.0.1:9000/
  ProxyPassReverse / http://127.0.0.1:9000/
  Redirect permanent /secure https://amiko.oddb.org
</VirtualHost>

<VirtualHost 62.12.131.45:443>
#<VirtualHost *:80>
  ProxyPreserveHost On
  ServerName amiko.oddb.org
  ProxyPass  /excluded !
  ProxyPass / http://127.0.0.1:9000/
  ProxyPassReverse / http://127.0.0.1:9000/
  SSLEngine on
  SSLCertificateFile /etc/letsencrypt/live/amiko.oddb.org/cert.pem
  SSLCertificateKeyFile /etc/letsencrypt/live/amiko.oddb.org/privkey.pem
  SSLCertificateChainFile /etc/letsencrypt/live/amiko.oddb.org/chain.pem
</VirtualHost>
```
## Setup SSL Encryption with Letsencrypt.org
```
cd /usr/local/src
https://github.com/certbot/certbot.git
https://github.com/certbot/certbot/blob/master/README.rst
```
## Install Nodejs with [nvm](https://github.com/creationix/nvm)
see instructions by [creationix](https://github.com/creationix/nvm#install--update-script)
```
curl -o- https://raw.githubusercontent.com/creationix/nvm/v0.34.0/install.sh | bash
nvm install 6.9.5
```
this will install 
* node v6.9.5
* npm v3.10.10

## Generating amikoweb-1.0-SNAPSHOT.zip
In the _$SRC_ Dir type `activator`. This will build `amikoweb-1.0-SNAPSHOT.zip`
