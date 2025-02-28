# Modular Backend Structure Example:

To Deploy:
* run build-and-push.sh
* restart backend azure app service

### Application (main method) <br>
### modules <br>
* Modules/stripe
  * controller
  * dto
  * entity
  * repository
  * service
* modules/cloudinary
  * controller
  * dto
  * entity
  * repository
  * service
* modules/checkout
  * controller
  * dto
  * entity
  * repository
  * service