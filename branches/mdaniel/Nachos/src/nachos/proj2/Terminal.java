package nachos.proj2;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class Terminal {
    public static void main(String[] args) throws Exception {
        if (0 == args.length) {
            System.err.println("Usage: Terminal command-line-here");
            System.exit(1);
        }
        java.util.List<String> command
        	= new java.util.ArrayList<String>(
        			java.util.Arrays.asList(args));
        if (!"java".equals(args[0])) {
        	command.add(0, "java");
        	command.add(1,"-classpath");
        	command.add(2,System.getProperty("java.class.path"));
        }
        System.err.println("LANUCH:>"+command);
            /*
            "java", 
            "-classpath", "../../../bin", 
            "nachos.machine.Machine", "-x", "echo.coff"
            */
        JFrame f = new JFrame("Terminal");
        JTextArea ta = new JTextArea(25,80);
        JScrollPane jsp = new JScrollPane(ta);
        //ta.setEditable(false);
        ta.setEnabled(false);
        ta.setDisabledTextColor(Color.BLACK);
        ta.setBackground(Color.WHITE);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setBackground(Color.BLACK);
        f.getContentPane().add(jsp);
        f.setSize(800,600);
        f.setVisible(true);
        Process proc = new ProcessBuilder(command)
                .start();
        //kill.addActionListener(new KillListener(proc));
        f.addWindowListener(new KillListener(proc));
        InputStream in = proc.getInputStream();
        InputStream err = proc.getErrorStream();
        Thread errT = new Thread(
                new StreamPump(err, System.err));
        errT.start();
        new Thread(new StreamPump(in, 
                    new TextAreaPrintStream(
                    		System.out, ta))).start();
        // PrintStream out = new PrintStream(proc.getOutputStream());
        OutputStream out = proc.getOutputStream();
        KeyListener kl = new MyKeyListener(ta, out);
        f.addKeyListener(kl);
        errT.join();
    }

    public static class KillListener 
            extends WindowAdapter 
            implements ActionListener 
    {
        public KillListener(Process proc) {
            this.proc = proc;
        }
        public void actionPerformed(ActionEvent e) {
        	run();
        }
        public void windowClosing(WindowEvent e) {
        	run();
        }
        public void run() { 
            proc.destroy();
        }
        Process proc;
    }

    public static class StreamPump implements Runnable
    {
        public StreamPump(InputStream in, OutputStream writeTo) {
            this.in = in;
            this.out = writeTo;
        }
        public void run() {
            int i;
            try {
            while (-1 != (i = in.read())) {
                out.write(i);
            }
            } catch (IOException ioe) {
                ioe.printStackTrace(System.err);
            }
        }
        InputStream in;
        OutputStream out;
    }

    public static class TextAreaPrintStream extends PrintStream
    {
        public TextAreaPrintStream(PrintStream delegate, JTextArea ta) {
            super(delegate);
            this.ta = ta;
        }
        public void write(int b) {
            ta.append(String.valueOf((char)b));
        }
        public void write(byte[] buf, int off, int len) {
            ta.append(new String(buf, off, len));
        }
        JTextArea ta;
    }

    public static class MyKeyListener implements KeyListener
    {
        public static boolean debug = Boolean.getBoolean("KeyListener.debug");
        public MyKeyListener(JTextArea ta, OutputStream out) {
            this.ta = ta;
            this.out = out;
        }
        public void keyTyped(KeyEvent e) {
            if (debug) {
                System.err.println("KT.E="+e);
            }
            char ch = e.getKeyChar();
            try {
            	out.write(ch);
            	out.flush();
            } catch (IOException ioe) {
            	ioe.printStackTrace(System.err);
            }
        }
        public void keyPressed(KeyEvent e) {
            if (debug) {
                System.err.println("KP.E="+e);
            }
        }
        public void keyReleased(KeyEvent e) {
            if (debug) {
                System.err.println("KR.E="+e);
            }
        }
        JTextArea ta;
        OutputStream out;
    } 
}
