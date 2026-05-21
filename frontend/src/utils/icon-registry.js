/**
 * Lucide icon registry — centralized name→component map
 *
 * 1. iconMap: all available icons for picker and dynamic rendering
 * 2. antToLucide: Ant Design icon name → Lucide name migration mapping
 * 3. categories: grouped icons for the picker UI
 */
import {
  Home, LayoutDashboard, Settings, Users, User, UserPlus, UserCheck, UserX,
  Shield, ShieldCheck, ShieldAlert, Lock, Unlock, Key, KeyRound,
  Menu, MenuSquare, AlignJustify, List, ListOrdered, ListTree,
  FileText, File, FilePlus, FileEdit, FileSearch, Files, FolderOpen, Folder, FolderPlus,
  Bell, BellRing, BellOff, Mail, MailOpen, MessageSquare, MessageCircle, Send,
  Search, Filter, SlidersHorizontal, Columns, LayoutGrid, Table, Kanban,
  Plus, Minus, X, Check, CheckCircle, XCircle, AlertTriangle, AlertCircle, Info,
  ChevronRight, ChevronDown, ChevronUp, ChevronLeft, ArrowRight, ArrowLeft, ArrowUp, ArrowDown,
  ExternalLink, Link, Unlink, Globe, GlobeLock,
  Eye, EyeOff, Copy, Clipboard, ClipboardCheck, Download, Upload, Share,
  Trash2, Pencil, PenLine, RotateCcw, RefreshCw, Undo, Redo,
  Calendar, CalendarDays, Clock, Timer, History,
  Image, Camera, Video, Mic, Volume2, VolumeX,
  Star, Heart, Bookmark, BookmarkCheck, Flag, Tag, Tags,
  Building, Building2, Store, Hotel, MapPin, Map, Navigation,
  CreditCard, Wallet, Receipt, DollarSign, TrendingUp, TrendingDown, BarChart3, PieChart, LineChart,
  Package, Box, ShoppingCart, ShoppingBag, Truck, Plane,
  Wifi, WifiOff, Bluetooth, Monitor, Smartphone, Tablet, Laptop, Printer, HardDrive,
  Database, Server, Cloud, CloudUpload, CloudDownload,
  Code, Terminal, Braces, FileCode, Bug, Wrench, Hammer, Cog,
  Zap, Lightbulb, Sun, Moon, Palette, Paintbrush,
  CircleDot, Circle, Square, Triangle, Hexagon,
  LogIn, LogOut, Power, ToggleLeft, ToggleRight,
  Layers, Component, Puzzle, Blocks,
  BookOpen, GraduationCap, Library, Newspaper,
  Activity, HeartPulse, Thermometer, Stethoscope,
  Bed, Bath, Utensils, Coffee, Wine, Cigarette,
  Car, Bus, Bike,
  TreePine, Flower, Leaf,
  ChartBar, FileUser, Baseline, CircleDollarSign, Grid2X2Plus, ArrowUpFromLine, Percent, FileBadge, Paperclip, LandPlot,
  LockKeyhole, SquarePlus, TabletSmartphone, SlidersVertical, Cpu, Contact, AlarmClockCheck, FileClock, UserRoundCheck,
  ListChecks, CalendarCheck, UserRoundCog, Sparkles, Handshake, Network, Share2, Inbox, ClockArrowDown, HelpCircle,
  JapaneseYen, FileBarChart,
} from 'lucide-vue-next'

/**
 * Complete icon map: name string → Vue component
 */
export const iconMap = {
  Home, LayoutDashboard, Settings, Users, User, UserPlus, UserCheck, UserX,
  Shield, ShieldCheck, ShieldAlert, Lock, Unlock, Key, KeyRound,
  Menu, MenuSquare, AlignJustify, List, ListOrdered, ListTree,
  FileText, File, FilePlus, FileEdit, FileSearch, Files, FolderOpen, Folder, FolderPlus,
  Bell, BellRing, BellOff, Mail, MailOpen, MessageSquare, MessageCircle, Send,
  Search, Filter, SlidersHorizontal, Columns, LayoutGrid, Table, Kanban,
  Plus, Minus, X, Check, CheckCircle, XCircle, AlertTriangle, AlertCircle, Info,
  ChevronRight, ChevronDown, ChevronUp, ChevronLeft, ArrowRight, ArrowLeft, ArrowUp, ArrowDown,
  ExternalLink, Link, Unlink, Globe, GlobeLock,
  Eye, EyeOff, Copy, Clipboard, ClipboardCheck, Download, Upload, Share,
  Trash2, Pencil, PenLine, RotateCcw, RefreshCw, Undo, Redo,
  Calendar, CalendarDays, Clock, Timer, History,
  Image, Camera, Video, Mic, Volume2, VolumeX,
  Star, Heart, Bookmark, BookmarkCheck, Flag, Tag, Tags,
  Building, Building2, Store, Hotel, MapPin, Map, Navigation,
  CreditCard, Wallet, Receipt, DollarSign, TrendingUp, TrendingDown, BarChart3, PieChart, LineChart,
  Package, Box, ShoppingCart, ShoppingBag, Truck, Plane,
  Wifi, WifiOff, Bluetooth, Monitor, Smartphone, Tablet, Laptop, Printer, HardDrive,
  Database, Server, Cloud, CloudUpload, CloudDownload,
  Code, Terminal, Braces, FileCode, Bug, Wrench, Hammer, Cog,
  Zap, Lightbulb, Sun, Moon, Palette, Paintbrush,
  CircleDot, Circle, Square, Triangle, Hexagon,
  LogIn, LogOut, Power, ToggleLeft, ToggleRight,
  Layers, Component, Puzzle, Blocks,
  BookOpen, GraduationCap, Library, Newspaper,
  Activity, HeartPulse, Thermometer, Stethoscope,
  Bed, Bath, Utensils, Coffee, Wine, Cigarette,
  Car, Bus, Bike,
  TreePine, Flower, Leaf,
  ChartBar, FileUser, Baseline, CircleDollarSign, Grid2X2Plus, ArrowUpFromLine, Percent, FileBadge, Paperclip, LandPlot,
  LockKeyhole, SquarePlus, TabletSmartphone, SlidersVertical, Cpu, Contact,AlarmClockCheck, FileClock, UserRoundCheck,
  ListChecks, CalendarCheck, UserRoundCog, Sparkles, Handshake, Network, Share2, Inbox, ClockArrowDown, HelpCircle,
  JapaneseYen, FileBarChart,
}

/**
 * Ant Design icon name → Lucide icon name mapping
 * DB に保存されている旧 AntDesign アイコン名を Lucide に変換
 */
export const antToLucide = {
  // 通用
  HomeOutlined: 'Home', HomeFilled: 'Home', HomeTwoTone: 'Home',
  DashboardOutlined: 'LayoutDashboard', DashboardFilled: 'LayoutDashboard',
  SettingOutlined: 'Settings', SettingFilled: 'Settings', SettingTwoTone: 'Settings',
  // ユーザー
  UserOutlined: 'User', UserAddOutlined: 'UserPlus', UserDeleteOutlined: 'UserX',
  TeamOutlined: 'Users', UsergroupAddOutlined: 'Users',
  // セキュリティ
  LockOutlined: 'Lock', UnlockOutlined: 'Unlock',
  SafetyOutlined: 'Shield', SafetyCertificateOutlined: 'ShieldCheck',
  KeyOutlined: 'Key', SecurityScanOutlined: 'ShieldAlert',
  // メニュー/リスト
  MenuOutlined: 'Menu', MenuFoldOutlined: 'AlignJustify', MenuUnfoldOutlined: 'AlignJustify',
  UnorderedListOutlined: 'List', OrderedListOutlined: 'ListOrdered',
  AppstoreOutlined: 'LayoutGrid', AppstoreFilled: 'LayoutGrid',
  BarsOutlined: 'List', TableOutlined: 'Table',
  // ファイル/フォルダ
  FileOutlined: 'File', FileFilled: 'File', FileTextOutlined: 'FileText',
  FileAddOutlined: 'FilePlus', FileSearchOutlined: 'FileSearch',
  FileExcelOutlined: 'FileText', FileWordOutlined: 'FileText', FilePdfOutlined: 'FileText',
  FolderOutlined: 'Folder', FolderFilled: 'Folder', FolderOpenOutlined: 'FolderOpen',
  FolderAddOutlined: 'FolderPlus', CopyOutlined: 'Copy',
  // 通知
  BellOutlined: 'Bell', BellFilled: 'BellRing',
  MailOutlined: 'Mail', MessageOutlined: 'MessageSquare',
  NotificationOutlined: 'BellRing', NotificationFilled: 'BellRing',
  CommentOutlined: 'MessageCircle', SendOutlined: 'Send',
  // 検索/フィルタ
  SearchOutlined: 'Search', FilterOutlined: 'Filter', FilterFilled: 'Filter',
  // 操作
  PlusOutlined: 'Plus', PlusCircleOutlined: 'Plus',
  MinusOutlined: 'Minus', MinusCircleOutlined: 'Minus',
  CloseOutlined: 'X', CloseCircleOutlined: 'XCircle',
  CheckOutlined: 'Check', CheckCircleOutlined: 'CheckCircle',
  EditOutlined: 'Pencil', EditFilled: 'Pencil', FormOutlined: 'PenLine',
  DeleteOutlined: 'Trash2', DeleteFilled: 'Trash2',
  ReloadOutlined: 'RefreshCw', SyncOutlined: 'RefreshCw',
  UndoOutlined: 'Undo', RedoOutlined: 'Redo', RollbackOutlined: 'RotateCcw',
  // 方向
  RightOutlined: 'ChevronRight', LeftOutlined: 'ChevronLeft',
  UpOutlined: 'ChevronUp', DownOutlined: 'ChevronDown',
  ArrowRightOutlined: 'ArrowRight', ArrowLeftOutlined: 'ArrowLeft',
  ArrowUpOutlined: 'ArrowUp', ArrowDownOutlined: 'ArrowDown',
  // 表示
  EyeOutlined: 'Eye', EyeInvisibleOutlined: 'EyeOff',
  DownloadOutlined: 'Download', UploadOutlined: 'Upload',
  ShareAltOutlined: 'Share', LinkOutlined: 'Link', DisconnectOutlined: 'Unlink',
  ExportOutlined: 'ExternalLink', ImportOutlined: 'Download',
  // リンク/グローバル
  GlobalOutlined: 'Globe', EnvironmentOutlined: 'MapPin',
  // 日付/時間
  CalendarOutlined: 'Calendar', CalendarFilled: 'CalendarDays',
  ClockCircleOutlined: 'Clock', HistoryOutlined: 'History',
  FieldTimeOutlined: 'Timer',
  // メディア
  PictureOutlined: 'Image', PictureFilled: 'Image',
  CameraOutlined: 'Camera', VideoCameraOutlined: 'Video',
  SoundOutlined: 'Volume2', AudioOutlined: 'Mic',
  // お気に入り/マーク
  StarOutlined: 'Star', StarFilled: 'Star',
  HeartOutlined: 'Heart', HeartFilled: 'Heart',
  BookOutlined: 'BookOpen', ReadOutlined: 'BookOpen',
  FlagOutlined: 'Flag', FlagFilled: 'Flag',
  TagOutlined: 'Tag', TagFilled: 'Tag', TagsOutlined: 'Tags',
  BookmarkOutlined: 'Bookmark',
  // ビル/ショップ
  BankOutlined: 'Building', BankFilled: 'Building',
  ShopOutlined: 'Store', ShopFilled: 'Store',
  ShoppingOutlined: 'ShoppingBag', ShoppingCartOutlined: 'ShoppingCart',
  // ビジネス
  CreditCardOutlined: 'CreditCard', WalletOutlined: 'Wallet',
  DollarOutlined: 'DollarSign', MoneyCollectOutlined: 'Receipt',
  RiseOutlined: 'TrendingUp', FallOutlined: 'TrendingDown',
  BarChartOutlined: 'BarChart3', PieChartOutlined: 'PieChart', LineChartOutlined: 'LineChart',
  FundOutlined: 'TrendingUp', StockOutlined: 'LineChart',
  // 配送
  CarOutlined: 'Car', CarFilled: 'Car',
  // デバイス
  DesktopOutlined: 'Monitor', MobileOutlined: 'Smartphone',
  TabletOutlined: 'Tablet', LaptopOutlined: 'Laptop',
  PrinterOutlined: 'Printer', WifiOutlined: 'Wifi',
  CloudOutlined: 'Cloud', CloudUploadOutlined: 'CloudUpload', CloudDownloadOutlined: 'CloudDownload',
  DatabaseOutlined: 'Database', HddOutlined: 'HardDrive',
  // 開発
  CodeOutlined: 'Code', ConsoleSqlOutlined: 'Terminal',
  BugOutlined: 'Bug', ToolOutlined: 'Wrench', BuildOutlined: 'Hammer',
  ApiOutlined: 'Braces', BlockOutlined: 'Blocks',
  // その他
  ThunderboltOutlined: 'Zap', BulbOutlined: 'Lightbulb',
  QuestionCircleOutlined: 'Info', InfoCircleOutlined: 'Info',
  ExclamationCircleOutlined: 'AlertCircle', WarningOutlined: 'AlertTriangle',
  LoginOutlined: 'LogIn', LogoutOutlined: 'LogOut',
  PoweroffOutlined: 'Power',
  SwitcherOutlined: 'ToggleLeft',
  ProfileOutlined: 'FileText', ProjectOutlined: 'Kanban',
  AuditOutlined: 'ClipboardCheck', SolutionOutlined: 'GraduationCap',
  ScheduleOutlined: 'CalendarDays', ReconciliationOutlined: 'Receipt',
  ContainerOutlined: 'Package', InboxOutlined: 'Box',
  ClusterOutlined: 'Server', DeploymentUnitOutlined: 'Layers',
  PartitionOutlined: 'ListTree', ApartmentOutlined: 'ListTree',
  NodeIndexOutlined: 'CircleDot', SubnodeOutlined: 'CircleDot',
  InteractionOutlined: 'Puzzle',
  TranslationOutlined: 'Globe',
  SkinOutlined: 'Palette',
  // 其他
  AlignLeftOutlined: 'ChartBar',
  UserSwitchOutlined: 'FileUser',
  FontColorsOutlined: 'Baseline',
  DollarCircleOutlined: 'CircleDollarSign',
  AppstoreAddOutlined: 'Grid2X2Plus',
  ToTopOutlined: 'ArrowUpFromLine',
  PercentageOutlined: 'Percent',
  FileMarkdownOutlined:  'FileBadge',
  PaperClipOutlined: 'Paperclip',
  BranchesOutlined: 'LandPlot',
  RadarChartOutlined: 'LockKeyhole',
  PlusSquareOutlined: 'SquarePlus',
  BoxPlotOutlined: 'Store',
  HeatMapOutlined: 'TabletSmartphone',
  ControlOutlined: 'SlidersVertical',
  UsbOutlined : 'Cpu',
  VerifiedOutlined : 'Contact',
  InsertRowBelowOutlined : 'Menu',
  RobotOutlined: 'AlarmClockCheck',
  ExceptionOutlined: 'FileClock',
  WindowsOutlined: 'LayoutDashboard'
}

/**
 * Categorized icon list for the picker
 */
export const iconCategories = [
  {
    label: '一般',
    icons: [
      'Home', 'LayoutDashboard', 'Settings', 'Search', 'Filter', 'SlidersHorizontal',
      'LayoutGrid', 'Table', 'Kanban', 'List', 'ListOrdered', 'ListTree', 'ListChecks',
      'Menu', 'MenuSquare', 'AlignJustify', 'Columns', 'Layers', 'Component', 'Puzzle', 'Blocks'
    ]
  },
  {
    label: 'ユーザー・セキュリティ',
    icons: [
      'User', 'Users', 'UserPlus', 'UserCheck', 'UserX', 'UserRoundCog',
      'Shield', 'ShieldCheck', 'ShieldAlert', 'Lock', 'Unlock', 'Key', 'KeyRound',
      'LogIn', 'LogOut', 'Power'
    ]
  },
  {
    label: 'ファイル・フォルダ',
    icons: [
      'File', 'FileText', 'FilePlus', 'FileEdit', 'FileSearch', 'FileCode', 'Files',
      'Folder', 'FolderOpen', 'FolderPlus', 'Inbox',
      'Copy', 'Clipboard', 'ClipboardCheck', 'Download', 'Upload', 'Share', 'Share2'
    ]
  },
  {
    label: '通知・メッセージ',
    icons: [
      'Bell', 'BellRing', 'BellOff', 'Mail', 'MailOpen',
      'MessageSquare', 'MessageCircle', 'Send'
    ]
  },
  {
    label: '操作',
    icons: [
      'Plus', 'Minus', 'X', 'Check', 'CheckCircle', 'XCircle',
      'Pencil', 'PenLine', 'Trash2', 'RefreshCw', 'RotateCcw', 'Undo', 'Redo',
      'Eye', 'EyeOff', 'ExternalLink', 'Link', 'Unlink',
      'ToggleLeft', 'ToggleRight'
    ]
  },
  {
    label: '方向',
    icons: [
      'ChevronRight', 'ChevronDown', 'ChevronUp', 'ChevronLeft',
      'ArrowRight', 'ArrowLeft', 'ArrowUp', 'ArrowDown'
    ]
  },
  {
    label: 'ステータス',
    icons: [
      'AlertTriangle', 'AlertCircle', 'Info', 'HelpCircle', 'Zap', 'Lightbulb', 'Sparkles',
      'Star', 'Heart', 'Bookmark', 'BookmarkCheck', 'Flag', 'Tag', 'Tags',
      'Activity', 'HeartPulse'
    ]
  },
  {
    label: '日付・時間',
    icons: ['Calendar', 'CalendarDays', 'CalendarCheck', 'Clock', 'ClockArrowDown', 'Timer', 'History']
  },
  {
    label: 'ビジネス',
    icons: [
      'Building', 'Building2', 'Store', 'Hotel', 'MapPin', 'Map', 'Navigation', 'Globe', 'GlobeLock',
      'CreditCard', 'Wallet', 'Receipt', 'DollarSign', 'JapaneseYen', 'Handshake',
      'TrendingUp', 'TrendingDown', 'BarChart3', 'FileBarChart', 'PieChart', 'LineChart',
      'ShoppingCart', 'ShoppingBag', 'Package', 'Box', 'Truck', 'Plane'
    ]
  },
  {
    label: 'デバイス・テクノロジー',
    icons: [
      'Monitor', 'Smartphone', 'Tablet', 'Laptop', 'Printer', 'HardDrive',
      'Wifi', 'WifiOff', 'Bluetooth', 'Network',
      'Database', 'Server', 'Cloud', 'CloudUpload', 'CloudDownload',
      'Code', 'Terminal', 'Braces', 'Bug', 'Wrench', 'Hammer', 'Cog'
    ]
  },
  {
    label: 'メディア',
    icons: ['Image', 'Camera', 'Video', 'Mic', 'Volume2', 'VolumeX', 'Sun', 'Moon', 'Palette', 'Paintbrush']
  },
  {
    label: 'ホテル・生活',
    icons: [
      'Bed', 'Bath', 'Utensils', 'Coffee', 'Wine', 'Cigarette',
      'Car', 'Bus', 'Bike',
      'BookOpen', 'GraduationCap', 'Library', 'Newspaper',
      'Thermometer', 'Stethoscope',
      'TreePine', 'Flower', 'Leaf'
    ]
  },
  {
    label: '図形',
    icons: ['CircleDot', 'Circle', 'Square', 'Triangle', 'Hexagon']
  }
]

/**
 * Resolve icon name: handles both Lucide names and legacy Ant Design names
 * Returns the Vue component or null
 */
export function resolveIcon(name) {
  if (!name) return null
  // Direct lucide match
  if (iconMap[name]) return iconMap[name]
  // Ant Design → Lucide migration
  const mapped = antToLucide[name]
  if (mapped && iconMap[mapped]) return iconMap[mapped]
  return null
}
