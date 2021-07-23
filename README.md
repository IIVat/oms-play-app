*How to run end2end test*
- ```sh run-e2e.sh```

**Note:** Please, try to run the test several times. Sometimes test failing, because some events goes into DLQ.
Not sure what is a cause right now, maybe are wrong settings of fake queue.
However, in most cases the test is successful. 

*How to run app*
- ```sh run.sh```

*AddCourier:*
```json
 aws --endpoint-url=http://localhost:9911 sns publish --topic-arn arn:aws:sns:eu-west-2:123450000001:events-topic --region eu-west-2 --message "{\"courierId\":\"a97261aa-2907-498e-aa24-3bce25590a46\",\"name\":\"first_courirer\",\"zone\":\"S\",\"isAvailable\":true}"  
```
*AddOrder:*
```json
aws --endpoint-url=http://localhost:9911 sns publish --topic-arn arn:aws:sns:eu-west-2:123450000001:events-topic --region eu-west-2 --message "{\"orderId\":\"b87261aa-2907-498e-aa24-3bce25590a46\",\"details\":\"Buy something\",\"zone\":\"S\",\"addedAt\":\"2021-07-18T10:22:10.170682Z\"}"
```
then you can observe the assignment message in assignment queue ```http://localhost:9325```


*Monitoring:*
- ```http://localhost:9325``` - sqs ui
  
*Swagger UI*
- ```http://localhost:9070/docs``` - swagger (sync api)

*Architecture schema:*
- schema.pdf - in the project root dir

**Solution and technical thoughts**

*The system consists of:*

- courier service
- order assignment service
- redis as storage
- sqs as queue
- sns as pub/sub
- cassandra could be used as events log db (but don't have a time for an implementation)

*How the system supposed to work*

- events-sqs is subscribed to events-sns
- events are published to events-sns
- events-sns fans out the events into events-sqs
- order assignment service (OAS) polls the events from events-sqs
- in OAS are two processing streams for handling AddOrder and AddCourier events
- when OAS consumes AddCourier events it just saves it in DB (Redis)
- Redis store couriers in sorted set, schema: ZONE "COURIERS SET" SCORE (which is number of assigned orders)
- if an order is assigned to a courier SCORE will be increased by one
  the idea is to have couriers with the lowest SCORE on a top of the set.
- if OAS consumed AddCourier with isAvailable = false, the courier must be deleted from Redis
- when OAS consumes AddOrder, it should take a courier with a lowest SCORE and assign the order to them
- then the assignment is published to assignment-sns -> assignment-sqs
- CourierService polls assignment-sqs and saves order-courier pair in DB
  there are two representation of the pair:
   - "orderId courierId" allows to query courier by orderId 
   - "courierId ordersIds" allows to query ordersList for a courier
- CourierService has HTTP API:
   - get orders by courierId
   - get courier for specific order
   - mark courier available, which spawns AddCourier event and publish it into event-sqs
    
*Notes:* 
 - I would prefer to have instead of Redis some RDBMS in courier service because of `one to many` relation of order-courier
   pair and for having more robust consistency of the data model.
-  I would connect OAS to Cassandra (or any other suitable storage) for storing an events log, because  
  it would be safe to have events replay mechanism for reliability purposes.
- markAvailable endpoint looks like addCourier in my implementation, possibly it breaks tasks
  constraint. However, I decided to spawn the event in the service, it looks naturally.
- I would prefer to have OrderService, CourierService and AssignmentService for handling
  correspondent events, but came up with the idea later.
- I had a fun working on the task, but honestly the task is time consuming. 8 hours for the task is arguable.
   

     
  


