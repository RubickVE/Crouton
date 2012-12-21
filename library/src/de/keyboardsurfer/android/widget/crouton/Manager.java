/*
 * Copyright 2012 Benjamin Weiss
 * Copyright 2012 Neofonie Mobile GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.keyboardsurfer.android.widget.crouton;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Manages the lifecycle of {@link Crouton}s.
 */
final class Manager extends Handler {
  private static final class Messages {
    private Messages() { /* no-op */
    }

    public static final int DISPLAY_CROUTON = 0xc2007;
    public static final int ADD_CROUTON_TO_VIEW = 0xc20074dd;
    public static final int REMOVE_CROUTON = 0xc2007de1;
  }

  private static Manager INSTANCE;

  private Queue<Crouton> croutonQueue;

  private Manager() {
    croutonQueue = new LinkedBlockingQueue<Crouton>();
  }

  /**
   * @return The currently used instance of the {@link Manager}.
   */
  static synchronized Manager getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new Manager();
    }

    return INSTANCE;
  }

  /**
   * Inserts a {@link Crouton} to be displayed.
   *
   * @param crouton
   *          The {@link Crouton} to be displayed.
   */
  void add(Crouton crouton) {
    croutonQueue.add(crouton);
    displayCrouton();
  }

  /**
   * Displays the next {@link Crouton} within the queue.
   */
  private void displayCrouton() {
    if (croutonQueue.isEmpty()) {
      return;
    }

    // First peek whether the Crouton has an activity.
    final Crouton currentCrouton = croutonQueue.peek();

    // If the activity is null we poll the Crouton off the queue.
    if (currentCrouton.getActivity() == null) {
      croutonQueue.poll();
    }

    if (!currentCrouton.isShowing()) {
      // Display the Crouton
      sendMessage(currentCrouton, Messages.ADD_CROUTON_TO_VIEW);
      if(currentCrouton.getLifecycleCallback() != null) {
    	  currentCrouton.getLifecycleCallback().onDisplayed();
      }
    } else {
      sendMessageDelayed(currentCrouton, Messages.DISPLAY_CROUTON, calculateCroutonDuration(currentCrouton));
    }
  }

  private long calculateCroutonDuration(Crouton crouton) {
    long croutonDuration = 0;
    croutonDuration += crouton.getStyle().durationInMilliseconds;
    croutonDuration += crouton.getInAnimation().getDuration();
    croutonDuration += crouton.getOutAnimation().getDuration();
    return croutonDuration;
  }

  /**
   * Sends a {@link Crouton} within a {@link Message}.
   *
   * @param crouton
   *          The {@link Crouton} that should be sent.
   * @param messageId
   *          The {@link Message} id.
   */
  private void sendMessage(Crouton crouton, final int messageId) {
    final Message message = obtainMessage(messageId);
    message.obj = crouton;
    sendMessage(message);
  }

  /**
   * Sends a {@link Crouton} within a delayed {@link Message}.
   *
   * @param crouton
   *          The {@link Crouton} that should be sent.
   * @param messageId
   *          The {@link Message} id.
   * @param delay
   *          The delay in milliseconds.
   */
  private void sendMessageDelayed(Crouton crouton, final int messageId, final long delay) {
    Message message = obtainMessage(messageId);
    message.obj = crouton;
    sendMessageDelayed(message, delay);
  }

  /*
   * (non-Javadoc)
   *
   * @see android.os.Handler#handleMessage(android.os.Message)
   */
  @Override
  public void handleMessage(Message message) {
    final Crouton crouton = (Crouton) message.obj;

    switch (message.what) {
      case Messages.DISPLAY_CROUTON: {
        displayCrouton();
        break;
      }

      case Messages.ADD_CROUTON_TO_VIEW: {
        addCroutonToView(crouton);
        break;
      }

      case Messages.REMOVE_CROUTON: {
        removeCrouton(crouton);
        if(crouton.getLifecycleCallback() != null) {
        	crouton.getLifecycleCallback().onRemoved();
        }
        break;
      }

      default: {
        super.handleMessage(message);
        break;
      }
    }
  }

  /**
   * Adds a {@link Crouton} to the {@link ViewParent} of it's {@link Activity}.
   *
   * @param crouton
   *          The {@link Crouton} that should be added.
   */
  private void addCroutonToView(Crouton crouton) {
    // don't add if it is already showing
    if (crouton.isShowing()) {
      return;
    }

    View croutonView = crouton.getView();
    if (croutonView.getParent() == null) {
      ViewGroup.LayoutParams params = croutonView.getLayoutParams();
      if (params == null) {
        params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      }
      // display Crouton in ViewGroup is it has been supplied
      if(crouton.getViewGroup() != null) {
    	  // TODO implement add to last position feature (need to align with how this will be requested for activity)
    	  crouton.getViewGroup().addView(croutonView, 0, params);	
      } else {
    	  crouton.getActivity().addContentView(croutonView, params);
      }
    }
    croutonView.startAnimation(crouton.getInAnimation());
    announceForAccessibilityCompat(crouton.getActivity(), crouton.getText());
    sendMessageDelayed(crouton, Messages.REMOVE_CROUTON,
      crouton.getStyle().durationInMilliseconds + +crouton.getInAnimation().getDuration());
  }

  /**
   * Removes the {@link Crouton}'s view after it's display
   * durationInMilliseconds.
   *
   * @param crouton
   *          The {@link Crouton} added to a {@link ViewGroup} and should be
   *          removed.
   */
  private void removeCrouton(Crouton crouton) {
    View croutonView = crouton.getView();
    ViewGroup croutonParentView = (ViewGroup) croutonView.getParent();

    if (croutonParentView != null) {
      croutonView.startAnimation(crouton.getOutAnimation());

      // Remove the Crouton from the queue.
      Crouton removed = croutonQueue.poll();

      // Remove the crouton from the view's parent.
      croutonParentView.removeView(croutonView);
      if (removed != null) {
        removed.detachActivity();
        removed.detachViewGroup();
        if(removed.getLifecycleCallback() != null) {
        	removed.getLifecycleCallback().onRemoved();
        }
        removed.detachLifecycleCallback();
      }

      // Send a message to display the next crouton but delay it by the out
      // animation duration to make sure it finishes
      sendMessageDelayed(crouton, Messages.DISPLAY_CROUTON, crouton.getOutAnimation().getDuration());
    }
  }

  /**
   * Removes a {@link Crouton} immediately, even when it's currently being
   * displayed.
   *
   * @param crouton
   *          The {@link Crouton} that should be removed.
   */
  void removeCroutonImmediately(Crouton crouton) {
    // if Crouton has already been displayed then it may not be in the queue (because it was popped).
    // This ensures the displayed Crouton is removed from its parent immediately, whether another instance
    // of it exists in the queue or not.
    // Note: crouton.isShowing() is false here even if it really is showing, as croutonView object in
	  // Crouton seems to be out of sync with reality!
	  if (crouton.getActivity() != null && crouton.getView() != null && crouton.getView().getParent() != null) {
	    ((ViewGroup) crouton.getView().getParent()).removeView(crouton.getView());
		  
	    // remove any messages pending for the crouton
	    removeAllMessagesForCrouton(crouton);
	  }
    // remove any matching croutons from queue
    if (croutonQueue != null) {
      final Iterator<Crouton> croutonIterator = croutonQueue.iterator();
      while (croutonIterator.hasNext()) {
        final Crouton c = croutonIterator.next();
        if (c.equals(crouton) && (c.getActivity() != null)) {
          // remove the crouton from the content view
          if (crouton.isShowing()) {
            ((ViewGroup) c.getView().getParent()).removeView(c.getView());
          }

          // remove any messages pending for the crouton
          removeAllMessagesForCrouton(c);

          // remove the crouton from the queue
          croutonIterator.remove();

          // we have found our crouton so just break
          break;
        }
      }
    }
  }

  /**
   * Removes all {@link Crouton}s from the queue.
   */
  void clearCroutonQueue() {
    removeAllMessages();

    if (croutonQueue != null) {
      // remove any views that may already have been added to the activity's
      // content view
      for (Crouton crouton : croutonQueue) {
        if (crouton.isShowing()) {
          ((ViewGroup) crouton.getView().getParent()).removeView(crouton.getView());
        }
      }
      croutonQueue.clear();
    }
  }

  /**
   * Removes all {@link Crouton}s for the provided activity. This will remove
   * crouton from {@link Activity}s content view immediately.
   */
  void clearCroutonsForActivity(Activity activity) {
    if (croutonQueue != null) {
      Iterator<Crouton> croutonIterator = croutonQueue.iterator();
      while (croutonIterator.hasNext()) {
        Crouton crouton = croutonIterator.next();
        if ((crouton.getActivity() != null) && crouton.getActivity().equals(activity)) {
          // remove the crouton from the content view
          if (crouton.isShowing()) {
            ((ViewGroup) crouton.getView().getParent()).removeView(crouton.getView());
          }

          removeAllMessagesForCrouton(crouton);

          // remove the crouton from the queue
          croutonIterator.remove();
        }
      }
    }
  }

  private void removeAllMessages() {
    removeMessages(Messages.ADD_CROUTON_TO_VIEW);
    removeMessages(Messages.DISPLAY_CROUTON);
    removeMessages(Messages.REMOVE_CROUTON);
  }

  private void removeAllMessagesForCrouton(Crouton crouton) {
    removeMessages(Messages.ADD_CROUTON_TO_VIEW, crouton);
    removeMessages(Messages.DISPLAY_CROUTON, crouton);
    removeMessages(Messages.REMOVE_CROUTON, crouton);

  }

  /**
   * Generates and dispatches an SDK-specific spoken announcement.
   * <p>
   * For backwards compatibility, we're constructing an event from scratch
   * using the appropriate event type. If your application only targets SDK
   * 16+, you can just call View.announceForAccessibility(CharSequence).
   * </p>
   *
   * note: AccessibilityManager is only available from API lvl 4.
   *
   * Adapted from https://http://eyes-free.googlecode.com/files/accessibility_codelab_demos_v2_src.zip
   * via https://github.com/coreform/android-formidable-validation
   *
   * @param context Used to get {@link AccessibilityManager}
   * @param text The text to announce.
   */
  public static void announceForAccessibilityCompat(Context context, CharSequence text) {
    if (Build.VERSION.SDK_INT >= 4) {
      AccessibilityManager accessibilityManager = (AccessibilityManager) context.getSystemService(
        Context.ACCESSIBILITY_SERVICE);
      if (!accessibilityManager.isEnabled()) {
        return;
      }

      // Prior to SDK 16, announcements could only be made through FOCUSED
      // events. Jelly Bean (SDK 16) added support for speaking text verbatim
      // using the ANNOUNCEMENT event type.
      final int eventType;
      if (Build.VERSION.SDK_INT < 16) {
        eventType = AccessibilityEvent.TYPE_VIEW_FOCUSED;
      } else {
        eventType = AccessibilityEventCompat.TYPE_ANNOUNCEMENT;
      }

      // Construct an accessibility event with the minimum recommended
      // attributes. An event without a class name or package may be dropped.
      final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
      event.getText().add(text);
      event.setClassName(Manager.class.getName());
      event.setPackageName(context.getPackageName());

      // Sends the event directly through the accessibility manager. If your
      // application only targets SDK 14+, you should just call
      // getParent().requestSendAccessibilityEvent(this, event);
      accessibilityManager.sendAccessibilityEvent(event);
    }
  }
}
