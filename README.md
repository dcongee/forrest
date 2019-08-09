# forrest

该工具模拟mysql的slave，从mysql上抓取binlog数据，解析成json格式，传输到目标数据源。
UPDATE数据格式：
{"TABLE_NAME":"T1","BEFOR_VALUE":{"T_TIME":"2018-08-19 18:06:21","T_MSECOND":"2018-08-19 06:49:09.000002","T_MS":"2018-08-19 18:06:21.394","T_MS5":"2018-08-19 06:49:09.10000","DATETIME_M6":"1018-08-19 06:49:09.001000","ID":"40","T_MS2":"2018-08-19 18:06:21.39","TIME_NULL":"null","T_MS4":"2018-08-19 06:49:09.0010"},"DATABASE_NAME":"WUHP","BINLOG_FILE":"mysql-bin.000028","SQL_TYPE":"UPDATE","BINLOG_POS":"4509","AFTER_VALUE":{"T_TIME":"2018-08-19 18:06:21","T_MSECOND":"2018-08-19 06:49:09.000002","T_MS":"2018-08-19 18:06:21.394","T_MS5":"2018-08-19 06:49:09.10000","DATETIME_M6":"1018-08-19 06:49:09.001000","ID":"4","T_MS2":"2018-08-19 18:06:21.39","TIME_NULL":"null","T_MS4":"2018-08-19 06:49:09.0010"}}

DELETE数据格式：
{"T_TIME":"2018-08-19 18:06:21","TABLE_NAME":"T1","DATABASE_NAME":"WUHP","T_MSECOND":"2018-08-19 06:49:09.000002","SQL_TYPE":"DELETE","T_MS5":"2018-08-19 06:49:09.10000","DATETIME_M6":"1018-08-19 06:49:09.001000","T_MS2":"2018-08-19 18:06:21.39","T_MS4":"2018-08-19 06:49:09.0010","BINLOG_FILE":"mysql-bin.000028","T_MS":"2018-08-19 18:06:21.394","BINLOG_POS":"9284","ID":"12","TIME_NULL":"2018-08-19 18:42:01.000"}

INSERT数据格式：
{"T_TIME":"2018-08-19 18:46:08","TABLE_NAME":"T1","DATABASE_NAME":"WUHP","T_MSECOND":"2018-08-19 06:49:09.000002","SQL_TYPE":"INSERT","T_MS5":"2018-08-19 06:49:09.10000","DATETIME_M6":"1018-08-19 06:49:09.001000","T_MS2":"2018-08-19 18:46:08.69","T_MS4":"2018-08-19 06:49:09.0010","BINLOG_FILE":"mysql-bin.000031","T_MS":"2018-08-19 18:46:08.698","BINLOG_POS":"613","ID":"40","TIME_NULL":"null"}

使用说明：

1、运行环境： x86_64 Centos6/7.x  jdk1.8
1.1、支持的MySQL版本：MySQL-5.6.x、MySQL-5.7.x
1.2、需要同步的mysql数据库binlog格式必须要设置为ROW格式：set global binlog_format='ROW'; 并将binlog_format=ROW添加到my.cnf配置文件中。


2、配置说明
2.1、主配置文件conf/fd.conf
#mysql的配置信息。
#mysql数据库用户名与密码;mysql用户最少需要replication slave,replication client,select,Reload,SUPER,Event,Execute,PROCESS权限，对information_Schema有select权限。
#通过语句增加mysql用户：grant replication slave,replication client,select,Reload,SUPER,Event,Execute,PROCESS on *.* to 'username'@'%' identified by 'passwd';
fd.mysql.host=192.168.137.101
fd.mysql.port=3306
fd.mysql.user=username
fd.mysql.passwd=password
fd.mysql.dbname=information_schema
fd.mysql.serverid=33130
#mysqlbinlog配置信息，将从下面的位置点开始同步；可以通过show master status获取位置点。fd.mysql.binlog.log.file.name为空，则从最新的位置点开始同步数据。
fd.mysql.binlog.log.file.name=
fd.mysql.binlog.log.pos=4


#######################replica destination start#################
#数据同步到目标数据源，可以支持：redis,rabbitmq,stdout,file,elasticsearch。若目标数据源为redis，则需要在redis.conf中配置redis信息；目标数据源为rabbitmq，则需要在rabbitmq.conf中配置rabbitmq信息。
#只能同步到一个目标数据源。
fd.ds.type=rabbitmq
#######################replica destination end###################

#filter db and table policy
#过滤策略：*.*表示同步所有的库与表的数据；test.*表示同步test库下所有表的数据；test.a表示同步test库中的a表数据；多个过滤策略用逗号隔开。
fd.replica.do.db.table=test.*,test1.*
#true表示同步update操作的数据，false则不同步
fd.replica.do.update.data=true
#true表示同步delete操作的数据，false则不同步
fd.replica.do.delete.data=true


#true 加载历史数据;false则不加载历史数据。
fd.load.history.data=true

#cache file
#记录当前同步数据的位置点。如果该文件存在，会优先从该文件的位置点进行同步。
fd.mysql.binlog.cache.file=/var/log/fd_binlog_pos.info


2.2、redis配置文件redis.conf
#redis配置信息；数据将同步到fd.ds.redis.key.name此参数定义的LIST队列中。
fd.ds.redis.host=192.168.100.1
fd.ds.redis.port=6379
fd.ds.redis.passwd=test123456
fd.ds.redis.key.name=MYSQL_LIST


2.3、rabbitmq配置文件rabbitmq.conf
fd.ds.rabbitmq.host=192.168.137.101
fd.ds.rabbitmq.port=5672
fd.ds.rabbitmq.user=test
fd.ds.rabbitmq.passwd=test
fd.ds.rabbitmq.exchange.name=MYSQL_EXCHANGE
fd.ds.rabbitmq.exchange.type=direct
fd.ds.rabbitmq.routing.key=MYSQL_ROUTING_KEY
fd.ds.rabbitmq.queue.name=MYSQL_QUEUE
fd.ds.rabbitmq.exchange.durable=true
fd.ds.rabbitmq.queue.durable=true


2.4、elasticsearch配置文件elasticsearch.conf
fd.ds.elasticsearch.host=192.168.137.101
fd.ds.elasticsearch.port=9300
fd.ds.elasticsearch.cluster.name=myclusterName
fd.ds.elasticsearch.client.transport.sniff=false
fd.ds.elasticsearch.id.with.mysql.primary=true

3、启动
#sh startup.sh



#注意事项 
#请先在ES中创建对应的索引。每一个MYSQL database对应一个ES中的INDEX。mysql中的每个表对应ES中的TYPE。MYSQL 主键值对ES中的document _id值。ES中的其他字段为MYSQL表中的字段
#ES中的INDEX名称为小写的database name，type名称为小写的table name。

#表必须包含主键，需要同步的表，若不包含主键，启动报错。

#表的主键名称不能发生变化，但主键值可以被UPDATE。

#修改表名称，会产生一个新type。

#表的主键如果为联合主键。则多个主键值与符号"_"拼接成ES的document id.

#fd.ds.elasticsearch.ignore.404参数设置为false时，如果要删除的数据没存在ES中，则会一直进行重试。建议设置为true

#truncate table 和drop table 不会删除ES中的索引与清空索引。

#请务必不要修改表字段的顺序。新增字段或者修改字段时，不要使用after修改表字段顺序。



