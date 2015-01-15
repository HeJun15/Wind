using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.IO;
using System.Diagnostics;
using System.Text.RegularExpressions;

namespace GetDLLVersion
{
    class Program
    {
        static void Main(string[] args)
        {
            string file = @"D:\Ground\Activity\Vendor\VendorService\Activity.Vendor.VendorService.Contract\bin\Debug";
            string shortfile = "VendorService_Activity.Vendor.VendorService.Contract";

            string pathfile = @"D:\Ground\Activity\Vendor\VendorService\Activity.Vendor.VendorService.Contract";

            StreamWriter sw = new StreamWriter(@"d:\Users\hejun\Desktop\" + shortfile + ".csv", false, System.Text.UTF8Encoding.UTF8);
            Console.SetOut(sw);
            
            // 获取目录内的文件名及版本信息以及引用文件路径
            GetFile(file, ".dll", pathfile, ".csproj");

            sw.Close();
        }

        // 参数：
        // string dir 指定的文件夹
        // string ext 文件类型的扩展名，如".dll", ".txt" , “.exe"
        static void GetFile(string dir, string ext, string pathdir, string pathext)
        {
            DirectoryInfo dirinfo = new DirectoryInfo(dir);             // bin目录信息
            DirectoryInfo pathdirinfo = new DirectoryInfo(pathdir);     // 查询.csproj文件

            List<string> dllpath = new List<string>();                  // 存储dll文件的路径

            foreach (FileInfo pathfi in pathdirinfo.GetFiles())
            {
                if (pathfi.Extension.ToUpper() == pathext.ToUpper())    // 查询.csproj文件
                {
                    FileStream fs = new FileStream(pathfi.FullName, FileMode.OpenOrCreate);
                    StreamReader sr = new StreamReader(fs, Encoding.Default);
 
                    while (!sr.EndOfStream)                             // 读到结尾退出
                    {
                        string temp = sr.ReadLine();
                        if (temp.Contains(".dll"))
                        {
                            string regexStr = @"\.\.\\\.\.\\.*?.dll";
                            Regex reg = new Regex(regexStr);
                            Match mc = reg.Match(temp);

                            dllpath.Add(mc.Value);
                        }
                    }
                    fs.Close();
                    sr.Close();
                }
            }

            foreach (FileInfo fi in dirinfo.GetFiles())
            {
                if (fi.Extension.ToUpper() == ext.ToUpper())
                {
                    FileVersionInfo myFileVersion = FileVersionInfo.GetVersionInfo(fi.FullName);
                    string dllName = fi.Name;
                    string dllFullName = fi.FullName;
                    string Text = myFileVersion.FileVersion;
                    string tmp = "NULL";
                    for (int i = 0; i < dllpath.Count; i++)
                    {
                        if (dllpath[i].Contains(dllName))
                        {
                            tmp = dllpath[i];
                            dllpath.Remove(dllpath[i]);
                            break;
                        }
                        else
                            tmp = "NULL";
                    }

                    string regexStr = "Vendor.*";
                    Regex reg = new Regex(regexStr);
                    Match mc = reg.Match(dllFullName);

                    dllFullName = mc.Value;

                    Console.WriteLine(dllName + "," + Text + "," + tmp + "," + dllFullName);
                }
            }
        }
    }
}
