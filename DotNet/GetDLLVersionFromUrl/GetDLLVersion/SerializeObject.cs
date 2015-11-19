using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using Newtonsoft.Json;

namespace GetDLLVersionFromUrl
{
    public class SerializeObject
    {
        public String StartUpTime { get; set; }
        public List<String> Assemblies { get; set; }

        public List<AssembliesObject> GetDll()
        {
            List<AssembliesObject> dllList = null;
            AssembliesObject dllTemp = null;

            foreach (String str in Assemblies)
            {
                dllList = new List<AssembliesObject>(); // 使用前一定先初始化
                dllTemp = new AssembliesObject();   // 使用前一定先初始化
               
                String[] strTemp = str.Split(',');

                if (strTemp.Length > 3)
                {
                    dllTemp.dllName = strTemp[0];
                    dllTemp.dllVersion = strTemp[1];
                    dllTemp.dllCulture = strTemp[2];
                    dllTemp.dllPublicKeyToken = strTemp[3];
                }

                dllList.Add(dllTemp);
                Console.WriteLine(dllTemp.dllVersion);
            }
            return dllList;
        }
    }

    public class AssembliesObject
    {
        public String dllName { get; set; }
        public String dllVersion { get; set; }
        public String dllCulture { get; set; }
        public String dllPublicKeyToken { get; set; }
    }
}
