using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Linq;
using System.Web;
using System.Web.Services;
using Arch.JobAgent.API;
using System.Web.Services.Protocols;

namespace CMessagingJobws
{
    /// <summary>
    /// Summary description for ConsumerService
    /// </summary>
    [WebServiceBinding(ConformsTo = WsiProfiles.BasicProfile1_1)]
    [ToolboxItem(false)]
    public class ConsumerService : JobWS
    {
    }
}
