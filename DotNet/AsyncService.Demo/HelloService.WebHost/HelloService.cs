using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using System.Threading.Tasks;
using CServiceStack.Common.Types;

namespace HelloService.WebHost
{
    public class HelloService : IHelloService
    {
        public CheckHealthResponseType CheckHealth(CheckHealthRequestType request)
        {
            return new CheckHealthResponseType();
        }

        public HelloResponseType Hello(HelloRequestType request)
        {
            return new HelloResponseType() { HelloResult = "Hello " + request.Name };
        }
    }
}