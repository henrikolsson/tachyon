(ns demogorgon.watch
  (:use [clj-native.direct :only [defclib loadlib typeof]]
        [clj-native.structs :only [byref byval]]
        [clj-native.callbacks :only [callback]])
  (:import [com.sun.jna Pointer]))

(def *constants*
     {:O_RDONLY 0x0000      ; open for reading only
      :O_WRONLY 0x0001      ; open for writing only
      :O_RDWR 0x0002        ; open for reading and writing
      :O_EVTONLY 0x8000     ; descriptor requested for event notifications only
      
                                        ; actions
      :EV_ADD 0x0001        ; add event to kq (implies enable)
      :EV_DELETE 0x0002     ; delete event from kq
      :EV_ENABLE 0x0004     ; enable event
      :EV_DISABLE 0x0008        ; disable event (not reported)
      
                                        ; flags
      :EV_ONESHOT 0x0010        ; only report one occurrence
      :EV_CLEAR 0x0020      ; clear event state after reporting
      :EV_SYSFLAGS 0xF000       ; reserved by system
      :EV_FLAG0 0x1000      ; filter-specific flag
      :EV_FLAG1 0x2000      ; filter-specific flag
      
                                        ; returned values
      :EV_EOF 0x8000        ; EOF detected
      :EV_ERROR 0x4000      ; error, data contains errno
      
      :EVFILT_VNODE -4
      
                                        ; data/hint fflags for EVFILT_VNODE, shared with userspace
      :NOTE_DELETE 0x00000001       ; vnode was removed
      :NOTE_WRITE 0x00000002        ; data contents changed
      :NOTE_EXTEND 0x00000004       ; size increased
      :NOTE_ATTRIB 0x00000008       ; attributes changed
      :NOTE_LINK 0x00000010     ; link count changed
      :NOTE_RENAME 0x00000020       ; vnode was renamed
      :NOTE_REVOKE  0x00000040      ;; vnode access was revoked
      })

(defn int-for-ptr []
  (condp = Pointer/SIZE
    4 "i32"
    8 "i64"))

(defclib
  kqueue-lib
  (:libname "c")
  (:structs
   (kevent-struct :ident i64 :filter i16 :flags i16 :fflags i32 :data i64 :udata void*)
   (timespec :tv_sec int :tv_nsec int))
  (:functions
   (kqueue [] int)
   (kevent [int kevent-struct* int kevent-struct* int timespec*] int)
   (open [constchar* int int] int)
   (close [int] int)))

(defn create-poller-kqueue [handle]
  (Thread.
   (fn []
     (loop [ret 0]
       (println "Polling..")
       (let [event (byref kevent-struct)
             events (kevent handle nil 0 event 1 nil)]
         (println (str "events: " events))
         (println (str "event: " event))
         (if (> events -1)
           (recur events))))
     (println "done"))
   (str "watcher/kqueue/" (System/currentTimeMillis))))

(defn create-watcher-kqueue []
  (loadlib kqueue-lib)
  (let [handle (kqueue)]
    (if (= handle -1)
      (throw (RuntimeException. "Failed to create handle"))
      (let [poller (create-poller-kqueue handle)]
        (.start poller)
        {:handle handle
         :poller poller
         :type :kqueue}))))

(defn create-watcher-inotify []
  (throw (UnsupportedOperationException. "Not implemented")))

(defn create-watcher []
  (let [os (.toLowerCase (System/getProperty "os.name"))]
    (if (or (> (.indexOf os "bsd") -1)
            (= os "mac os x"))
      (create-watcher-kqueue)
      (if (> (.indexOf os "linux") -1)
        (create-watcher-inotify)
        (throw (RuntimeException. (str "No implementation for " (System/getProperty "os.name"))))))))


(defmulti shutdown-watcher :type)
(defmethod shutdown-watcher :kqueue [watcher]
  (close (:handle watcher)))

(defmulti add-file-watch :type)
(defmethod add-file-watch :kqueue [watcher file]
  (let [change (byref kevent-struct)
        fd (open file (:O_EVTONLY *constants*) 0)]
    (set! (.ident change) fd)
    (set! (.filter change) (:EVFILT_VNODE *constants*))
    (set! (.flags change) (bit-or (:EV_ADD *constants*) (:EV_CLEAR *constants*)))
    (set! (.fflags change) (:NOTE_DELETE *constants*))
    (kevent (:handle watcher) change 1 nil 0 nil)))

(defn lollers []
  (let [watcher (create-watcher)]
    (Thread/sleep 1000)
    (add-file-watch watcher "/tmp/foo")
    (println "Added file watch")
    (Thread/sleep 20000)
    (println "Done")
    (shutdown-watcher watcher)
    (Thread/sleep 5000)
    (println "Exititing")))
