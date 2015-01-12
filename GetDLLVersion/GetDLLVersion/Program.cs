using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.IO;
using System.Diagnostics;

namespace GetDLLVersion
{
    class Program
    {
        static void Main(string[] args)
        {
            string file = @"D:\Ground\Activity\Vendor\VendorService\Activity.Vendor.VendorService.Service\bin";
            string shortfile = "VendorService_Activity.Vendor.VendorService.Service";
            StreamWriter sw = new StreamWriter(@"d:\Users\hejun\Desktop\" + shortfile + ".js", false, System.Text.UTF8Encoding.UTF8);
            Console.SetOut(sw);
            
            // 获取目录内的文件名及版本信息
            GetFileCount(file, ".dll");

            sw.Close();
        }

        // 参数：
        // string dir 指定的文件夹
        // string ext 文件类型的扩展名，如".dll", ".txt" , “.exe"
        static void GetFileCount(string dir, string ext)
        {
            DirectoryInfo dirinfo = new DirectoryInfo(dir);
            foreach (FileInfo fi in dirinfo.GetFiles())
            {
                if (fi.Extension.ToUpper() == ext.ToUpper())
                {
                    FileVersionInfo myFileVersion = FileVersionInfo.GetVersionInfo(fi.FullName);
                    string Text = myFileVersion.FileVersion; 

                    Console.WriteLine(fi.Name + "   " + Text);
                }
            }
        }
    }
}
