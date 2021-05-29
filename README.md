# AWS Architecture

The architecture is composed by the following services: web servers, load balancer and auto-scaler.

# AWS Parameters

The AWS parameters and all other configurations of this project can be found in the class Config of the loadbalancer project, especifically the sub-path:
"loadbalancer/src/main/java/pt/tecnico/ulisboa/cnv/Configs.java".


# Structure

This file is currently contained in the root directory.

In this same directory there is the loadbalancer folder
which contains the project of the load balancer plus auto
scaler. The structure of the folders of this project are described in the implementation section of the updated report.

In the pt/ulisboa/tecnico/cnv directory is contained: - The server folder, with the web server code edited.

- The solver folder with the already instrumented algorithms.

- The deploy folder with the EC2Launch.java class which contains some AWS SDK code to create the MSS, instances, insert initial data in the MSS... (this class is diferent from the AWS Handler class used by the Load Balancer to manage EC2 instances.)

- The BIT folder with the classes StatisticsTool.Java with the instrumentation code and the class PerThreadStats that stores information for each thead.


