using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using CServiceStack.WebHost.Endpoints;

namespace HelloService.WebHost
{
    public class Global : System.Web.HttpApplication
    {
        AppHost apphost;

        protected void Application_Start(object sender, EventArgs e)
        {
            apphost = new AppHost();
            apphost.Init();
        }

        protected void Application_End(object sender, EventArgs e)
        {
            apphost.Dispose();
        }
    }

    public class AppHost : AppHostBase
    {
        public AppHost()
            : base(typeof(HelloService).Assembly)
        { 
        }

        public override void Configure(Funq.Container container)
        {
            
        }
    }
}