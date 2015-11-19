using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using System.Configuration;
using System.Net;
using System.Threading.Tasks;
using CServiceStack.Common.Types;
using CServiceStack.ServiceHost;
using System.IO;
using System.Threading;
using HelloService.WebClient;

namespace GreetingService.WebHost
{
    public class GreetingService : IGreetingService, IRequiresHttpRequest, IRequiresHttpResponse
    {
        private static HelloServiceClient helloServiceClient;
        internal static string ServiceUrl;

        /// <summary>
        /// 当前请求
        /// </summary>
        public IHttpRequest HttpRequest { get; set; }
        /// <summary>
        /// 当前响应
        /// </summary>
        public IHttpResponse HttpResponse { get; set; }

        static GreetingService()
        {
            ServiceUrl = ConfigurationManager.AppSettings["ServiceUrl"];
            if (string.IsNullOrWhiteSpace(ServiceUrl))
                throw new ConfigurationErrorsException("Missing 'ServiceUrl' setting!");

            helloServiceClient = HelloServiceClient.GetInstance(ServiceUrl);
        }

        public CheckHealthResponseType CheckHealth(CheckHealthRequestType request)
        {
            return new CheckHealthResponseType();
        }

        public Task<GreetingResponseType> Greeting(GreetingRequestType request)
        {
            HelloServiceClient helloServiceClient = HelloServiceClient.GetInstance();
            var task = helloServiceClient.StartIOCPTaskOfHello(new HelloRequestType() { Name = request.Name });
            return task.ContinueWith(t =>
            {
                var response = new GreetingResponseType();
                if (task.Exception != null) // 判断是否出现了异常
                {
                    var exceptions = task.Exception.InnerExceptions;
                    response.ResponseStatus = new ResponseStatusType()
                    {
                        Ack = AckCodeType.Failure,
                        Errors = new List<ErrorDataType>()
                    };
                    foreach (var exception in exceptions)
                    {
                        response.ResponseStatus.Errors.Add(new ErrorDataType()
                        {
                            Message = exception.Message,
                            StackTrace = exception.StackTrace,
                        });
                    }
                    return response;
                }
                HttpResponse.AddHeader("via", "async"); // 使用响应上下文
                
                var helloResponse = task.Result;
                return new GreetingResponseType() { GreetingResult = helloResponse.HelloResult };
            });
        }
    }
}