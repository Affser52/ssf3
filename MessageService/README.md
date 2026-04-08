ru/mescat/message/controller - точка входа в программу.
Все сервисы обращаються через эти api обязательно передавая 
айди юсера лополнительным параметром X-User-Id.
Никто кроме сервисов не имеет к нему доступ.

ru/mescat/rest - обращения к постороним сервисам.

ru/mescat/user - сервис для UserService отвчеающий за оптравку и получения запрсов
для данного сервиса.

ru/mescat/message/service - вся основная бизнес логика для взаимодействия с данными
с разными данными в системе и выполнения всех действий. 

ru/mescat/message/repository - репозитории предназаначеные только для
взаимодействия с HiberNate

ru/mescat/message/map - конвертируем классы в другие

ru/mescat/message/exception - кастомные exception

ru/mescat/message/entity - бд обхекты представленные в java классы

ru/mescat/message/dto - все dto предназначнеые для 
