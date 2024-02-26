# san-live-plugins
For intellij: 

Custom plugin for [LivePlugins](https://github.com/dkandalov/live-plugin)

## Use
### 1.Install Live Plugins

 ![](./doc/Screenshot_01.png)

### 2.Open the plugin window

#### 2.1 From Tool Window 
 ![](./doc/Screenshot_02.png)

#### 2.2 From Actions Search
![](./doc/Screenshot_03.png)

### 3.Create a new or import plugin
#### 3.1 Import
![](./doc/Screenshot_04.png)  
![](./doc/Screenshot_05.png)  
This window will show you the path to the LivePlugins plugin, and the git repository for clone will be underneath it.
So when using this repository, you need to manually copy the required plugins to the Parent Directory path.
For example, using EasyCode:
![](./doc/Screenshot_06.png)

#### 4 Turn on autorun
![](./doc/Screenshot_07.png)

live-plugins is a git repository, not a standard plugin, so it will fail to run and can be deleted from the Parent Directory when not needed.

## NOTE
When upgrading a major version of IDEA. The Parent Directory will change, so you can manually copy it over from the old directory