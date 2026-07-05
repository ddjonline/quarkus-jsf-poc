INSERT INTO shipment VALUES
 ('00004821763','PRO-4821763','IN_TRANSIT','Memphis, TN','Nashville, TN',
  'Hartfield Industrial Supply','Valley Manufacturing Co.','Hydraulic Equipment',
  1240,6,'Jun 26, 2026 - 07:42 AM','Marcus D. Reyes','(901) 555-0182',
  'Jackson, TN - I-40 East MM 83','Jun 26, 2026 - 10:15 AM','Jun 26, 2026 - 2:00-4:00 PM'),
 ('00003390045','PRO-3390045','DELIVERED','Birmingham, AL','Atlanta, GA',
  'Southline Distribution','Peachtree Retail Group','Palletized Goods',
  2180,12,'Jun 25, 2026 - 09:10 AM','Andre L. Coleman','(205) 555-0147',
  'Atlanta, GA - Delivered','Jun 25, 2026 - 04:38 PM','Jun 25, 2026 - Delivered');

INSERT INTO tracking_event (pro_number,seq,time_label,title,location,state) VALUES
 ('00004821763',0,'07:42 AM','Picked up','Memphis, TN - Hartfield Industrial','COMPLETED'),
 ('00004821763',1,'08:55 AM','Departed terminal','Memphis, TN - Terminal 7','COMPLETED'),
 ('00004821763',2,'10:15 AM','En route','Jackson, TN - I-40 East MM 83','CURRENT'),
 ('00003390045',0,'09:10 AM','Picked up','Birmingham, AL - Southline DC','COMPLETED'),
 ('00003390045',1,'11:05 AM','Departed terminal','Birmingham, AL - Terminal 3','COMPLETED'),
 ('00003390045',2,'02:20 PM','Out for delivery','Atlanta, GA - Terminal 1','COMPLETED'),
 ('00003390045',3,'04:38 PM','Delivered','Atlanta, GA - Peachtree Retail','COMPLETED');
