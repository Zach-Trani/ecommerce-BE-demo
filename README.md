### Link to Hosted Site
https://lively-moss-09bc30c10.4.azurestaticapps.net/ <br>

### Link to Front End Repo
https://github.com/Zach-Trani/ecommerce-FE-demo <br>

### Back End Modular Architecture <br>
* modules/product - database product entries
  * controller
  * entity
  * repository
* modules/stripe - stripe checkout integration for a cart of products
  * controller
  * dto
  * service
* modules/transactions - stripe webhook integration for recording transactions in the database
  * controller
  * entity
  * repository
* modules/customer - customer contact and shipping records in the database
  * controller
  * entity
  * repository
