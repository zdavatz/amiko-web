# amiko-web
AmiKo auf dem Web mit Play 2.5.2
## Install Activator
```https://www.lightbend.com/activator/download```
```Add it to your Path in .bashrc```
```export PATH=/usr/local/src/activator-dist-1.3.10/bin:$PATH```
## Clone the Software
git clone https://github.com/zdavatz/amiko-web.git

## Update the Software
```cd /usr/local/src/amiko-web
git pull
activator -java-home /usr/local/src/jdk1.8.0_92/
cd /var/www/amiko.oddb.org/
rm amikoweb-1.0-SNAPSHOT.zip
mv /usr/local/src/amiko-web/target/universal/amikoweb-1.0-SNAPSHOT.zip /var/www/amiko.oddb.org/
rm -r /var/www/amiko.oddb.org/amikoweb-1.0-SNAPSHOT
unzip amikoweb-1.0-SNAPSHOT.zip
cp -r /var/www/amiko.oddb.org/amikoweb-1.0-SNAPSHOT/* /var/www/amiko.oddb.org/
vim conf/application.conf
svc -h /service/amiko.oddb.org```

## Update the database
```cd /var/www/amiko.oddb.org/sqlite
rm amiko_db_full_idx_de.db
wget http://pillbox.oddb.org/amiko_db_full_idx_de.zip
unzip amiko_db_full_idx_de.zip
rm amiko_db_full_idx_de.zip
svc -h /service/amiko.oddb.org```
